package org.tradinggate.backend.settlementIntegrity.recon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconBatchStatus;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconFailureCode;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconRetryPolicyType;
import org.tradinggate.backend.settlementIntegrity.recon.repository.ReconBatchRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Profile("recon")
public class ReconBatchService {

    public static final ReconRetryPolicyType RETRY_POLICY = ReconRetryPolicyType.REUSE_SAME_ATTEMPT_OVERWRITE_DIFFS;

    private final ReconBatchRepository reconBatchRepository;

    @Transactional(readOnly = true)
    public ReconBatch findById(Long id) {
        return reconBatchRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("ReconBatch not found. id=" + id));
    }

    @Transactional
    public ReconBatch getOrCreatePending(Long clearingBatchId, LocalDate businessDate, String snapshotKey) {
        ReconBatch latest = reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(clearingBatchId).orElse(null);
        // 기본 recon 정책은 same-attempt 재사용 + diff overwrite 이다.
        int attempt = (latest == null) ? 1 : latest.getAttempt();

        return reconBatchRepository.findByClearingBatchIdAndAttempt(clearingBatchId, attempt)
                .orElseGet(() -> createPendingWithUniqGuard(clearingBatchId, businessDate, snapshotKey, attempt));
    }

    /**
     * 필요 시 운영자가 이력을 분리하고 싶을 때 사용할 수 있는 대안 경로.
     * 기본 정책(REUSE_SAME_ATTEMPT_OVERWRITE_DIFFS)과 분리해서 명시적으로 제공한다.
     */
    @Transactional
    public ReconBatch createRetryPendingNextAttempt(Long reconBatchId) {
        ReconBatch base = reconBatchRepository.findById(reconBatchId)
                .orElseThrow(() -> new IllegalStateException("ReconBatch not found. id=" + reconBatchId));
        int nextAttempt = Math.max(1, base.getAttempt() + 1);

        return reconBatchRepository.findByClearingBatchIdAndAttempt(base.getClearingBatchId(), nextAttempt)
                .orElseGet(() -> createRetryPendingWithUniqGuard(base, nextAttempt));
    }

    @Transactional
    public ReconBatch getOrCreatePendingStandalone(LocalDate businessDate) {
        // clearing_batch_id=0은 standalone(live) recon 전용 예약값이다.
        return getOrCreatePendingStandalone(businessDate, false);
    }

    /**
     * standalone 수동 재실행 경로: 완료된 batch를 재사용하지 않고 새 attempt를 생성한다.
     */
    @Transactional
    public ReconBatch createStandaloneRetryPendingNextAttempt(LocalDate businessDate) {
        return getOrCreatePendingStandalone(businessDate, true);
    }

    @Transactional
    public boolean tryMarkRunning(Long reconBatchId) {
        int updated = reconBatchRepository.tryMarkRunning(
                reconBatchId,
                ReconBatchStatus.PENDING,
                ReconBatchStatus.RUNNING,
                Instant.now()
        );
        return updated == 1;
    }

    @Transactional
    public void markSuccess(Long id) {
        reconBatchRepository.markSuccess(id, ReconBatchStatus.SUCCESS, Instant.now());
    }

    @Transactional
    public void markSuccessWithSummary(Long id, long diffCount, long highSeverityCount, BigDecimal totalAbsDiff) {
        reconBatchRepository.markSuccessWithSummary(
                id,
                ReconBatchStatus.SUCCESS,
                Instant.now(),
                Math.max(0L, diffCount),
                Math.max(0L, highSeverityCount),
                totalAbsDiff == null ? BigDecimal.ZERO : totalAbsDiff
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, ReconFailureCode code, String detail) {
        String remark = formatRemark(code, detail);
        reconBatchRepository.markFailed(id, ReconBatchStatus.FAILED, Instant.now(), code, remark);
    }

    private ReconBatch createPendingWithUniqGuard(Long clearingBatchId, LocalDate businessDate, String snapshotKey, int attempt) {
        try {
            return reconBatchRepository.saveAndFlush(ReconBatch.pending(clearingBatchId, businessDate, snapshotKey, attempt));
        } catch (DataIntegrityViolationException e) {
            return reconBatchRepository.findByClearingBatchIdAndAttempt(clearingBatchId, attempt).orElseThrow(() -> e);
        }
    }

    private ReconBatch createRetryPendingWithUniqGuard(ReconBatch base, int nextAttempt) {
        try {
            return reconBatchRepository.saveAndFlush(ReconBatch.retryPendingOf(base, nextAttempt));
        } catch (DataIntegrityViolationException e) {
            return reconBatchRepository.findByClearingBatchIdAndAttempt(base.getClearingBatchId(), nextAttempt)
                    .orElseThrow(() -> e);
        }
    }

    private String formatRemark(ReconFailureCode code, String detail) {
        String safeDetail = detail == null ? "" : detail;
        String raw = code.getCode() + "|" + safeDetail;
        return raw.length() <= 255 ? raw : raw.substring(0, 255);
    }

    private ReconBatch getOrCreatePendingStandalone(LocalDate businessDate, boolean forceNextAttempt) {
        final long standaloneClearingBatchId = 0L;
        final String snapshotKey = "LIVE-" + businessDate;

        ReconBatch latestForDate = reconBatchRepository
                .findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(standaloneClearingBatchId, businessDate)
                .orElse(null);

        if (!forceNextAttempt && latestForDate != null) {
            return latestForDate;
        }

        // 주의:
        // 현재 유니크 키가 (clearing_batch_id, attempt)이므로 standalone(0L)의 attempt는 날짜를 넘어서 전역 네임스페이스다.
        // 스키마 변경 전까지는 standalone 전체의 최대 attempt를 기준으로 증가시킨다.
        ReconBatch globalLatestStandalone = reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(standaloneClearingBatchId)
                .orElse(null);
        int nextGlobalAttempt = (globalLatestStandalone == null) ? 1 : Math.max(1, globalLatestStandalone.getAttempt() + 1);

        if (forceNextAttempt && latestForDate != null) {
            return reconBatchRepository.findByClearingBatchIdAndAttempt(standaloneClearingBatchId, nextGlobalAttempt)
                    .orElseGet(() -> createRetryPendingWithUniqGuard(latestForDate, nextGlobalAttempt));
        }

        return reconBatchRepository.findByClearingBatchIdAndAttempt(standaloneClearingBatchId, nextGlobalAttempt)
                .orElseGet(() -> createPendingWithUniqGuard(standaloneClearingBatchId, businessDate, snapshotKey, nextGlobalAttempt));
    }
}
