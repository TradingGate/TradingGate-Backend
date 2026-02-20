package org.tradinggate.backend.recon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.recon.domain.ReconBatch;
import org.tradinggate.backend.recon.domain.e.ReconBatchStatus;
import org.tradinggate.backend.recon.domain.e.ReconFailureCode;
import org.tradinggate.backend.recon.repository.ReconBatchRepository;

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Profile("recon")
public class ReconBatchService {

    private final ReconBatchRepository reconBatchRepository;

    @Transactional(readOnly = true)
    public ReconBatch findById(Long id) {
        return reconBatchRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("ReconBatch not found. id=" + id));
    }

    @Transactional
    public ReconBatch getOrCreatePending(Long clearingBatchId, LocalDate businessDate, String snapshotKey) {
        ReconBatch latest = reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(clearingBatchId).orElse(null);
        int attempt = (latest == null) ? 1 : latest.getAttempt(); // 기본은 같은 attempt를 재사용(배치 retry는 별도 정책으로)

        return reconBatchRepository.findByClearingBatchIdAndAttempt(clearingBatchId, attempt)
                .orElseGet(() -> createPendingWithUniqGuard(clearingBatchId, businessDate, snapshotKey, attempt));
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

    private String formatRemark(ReconFailureCode code, String detail) {
        String safeDetail = detail == null ? "" : detail;
        String raw = code.getCode() + "|" + safeDetail;
        return raw.length() <= 255 ? raw : raw.substring(0, 255);
    }
}
