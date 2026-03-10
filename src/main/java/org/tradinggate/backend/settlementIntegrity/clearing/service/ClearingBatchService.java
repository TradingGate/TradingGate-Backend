package org.tradinggate.backend.settlementIntegrity.clearing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingFailureCode;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingRetryPolicyType;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingBatchRepository;

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Profile("clearing")
public class ClearingBatchService {

    public static final ClearingRetryPolicyType RETRY_POLICY = ClearingRetryPolicyType.NEW_ATTEMPT_FROM_FAILED_BATCH;

    private final ClearingBatchRepository clearingBatchRepository;

    private static final int INTRADAY_BUCKET_MINUTES = 10;

    @Transactional(readOnly = true)
    public ClearingBatch findById(Long id) {
        return clearingBatchRepository.findById(id).orElseThrow(() -> new IllegalStateException("ClearingBatch not found after acquire. batchId=" + id));
    }

    /**
     * 실행 키(businessDate/batchType/runKey/attempt)에 해당하는 PENDING 배치를 생성하거나 재사용한다.
     */
    @Transactional
    public ClearingBatch getOrCreatePending(LocalDate businessDate, ClearingBatchType batchType, String scope) {
        String runKey = defaultRunKey(batchType);
        return getOrCreatePending(businessDate, batchType, runKey, 1, normalizeScope(scope));
    }

    @Transactional
    public ClearingBatch getOrCreatePending(LocalDate businessDate, ClearingBatchType batchType, String runKey, int attempt, String scope) {
        String normalizedScope = normalizeScope(scope);
        return clearingBatchRepository.findByBusinessDateAndBatchTypeAndRunKeyAndAttempt(businessDate, batchType, runKey, attempt)
                .orElseGet(() -> createPendingWithUniqGuard(businessDate, batchType, runKey, attempt, normalizedScope));
    }

    /**
     * Clearing 재시도 정책(MVP):
     * FAILED 배치를 재사용하지 않고 새 attempt를 생성한다.
     */
    @Transactional
    public ClearingBatch createRetryPendingFromFailed(Long failedBatchId) {
        ClearingBatch failed = clearingBatchRepository.findById(failedBatchId)
                .orElseThrow(() -> new IllegalStateException("ClearingBatch not found. batchId=" + failedBatchId));

        if (failed.getStatus() != ClearingBatchStatus.FAILED) {
            throw new IllegalStateException("Retry source batch is not FAILED. batchId=" + failedBatchId
                    + ", status=" + failed.getStatus());
        }

        int nextAttempt = Math.max(1, failed.getAttempt() + 1);
        return clearingBatchRepository.findByBusinessDateAndBatchTypeAndRunKeyAndAttempt(
                        failed.getBusinessDate(),
                        failed.getBatchType(),
                        failed.getRunKey(),
                        nextAttempt
                )
                .orElseGet(() -> createRetryPendingWithUniqGuard(failed, nextAttempt));
    }

    /**
     * DB 단일 SQL에서 배치 선점과 워터마크(`max_ledger_id`) 저장을 원자적으로 수행한다.
     */
    @Transactional
    public boolean tryMarkRunningWithDbWatermark(Long batchId) {
        int updated = clearingBatchRepository.tryMarkRunningWithDbWatermark(
                batchId,
                ClearingBatchStatus.PENDING.name(),
                ClearingBatchStatus.RUNNING.name(),
                Instant.now()
        );
        return updated == 1;
    }

    @Transactional
    public void markSuccess(Long batchID) {
        clearingBatchRepository.markSuccess(batchID, ClearingBatchStatus.SUCCESS, Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long batchId, ClearingFailureCode failureCode, String detail) {
        String remark = formatRemark(failureCode, detail);
        clearingBatchRepository.markFailed(batchId, ClearingBatchStatus.FAILED, Instant.now(), failureCode, remark);
    }

    private ClearingBatch createPendingWithUniqGuard(LocalDate businessDate, ClearingBatchType batchType, String runKey, int attempt, String scope) {
        try {
            ClearingBatch batch = ClearingBatch.pending(businessDate, batchType, runKey, attempt, normalizeScope(scope));
            return clearingBatchRepository.saveAndFlush(batch);
        } catch (DataIntegrityViolationException e) {
            // 다른 워커가 먼저 생성한 경우, 같은 실행 키의 배치를 다시 조회해 재사용한다.
            return clearingBatchRepository.findByBusinessDateAndBatchTypeAndRunKeyAndAttempt(businessDate, batchType, runKey, attempt).orElseThrow(() -> e);
        }
    }

    private ClearingBatch createRetryPendingWithUniqGuard(ClearingBatch failedBatch, int nextAttempt) {
        try {
            return clearingBatchRepository.saveAndFlush(ClearingBatch.retryPendingOf(failedBatch, nextAttempt));
        } catch (DataIntegrityViolationException e) {
            return clearingBatchRepository.findByBusinessDateAndBatchTypeAndRunKeyAndAttempt(
                    failedBatch.getBusinessDate(),
                    failedBatch.getBatchType(),
                    failedBatch.getRunKey(),
                    nextAttempt
            ).orElseThrow(() -> e);
        }
    }

    private String formatRemark(ClearingFailureCode code, String detail) {
        // 운영자가 stack trace 없이도 실패 유형을 집계할 수 있도록 코드 prefix를 남긴다.
        String safeDetail = detail == null ? "" : detail;
        String raw = code.getCode() + "|" + safeDetail;
        return raw.length() <= 255 ? raw : raw.substring(0, 255);
    }

    private String normalizeScope(String scope) {
        // null/blank scope를 동일 표현(빈 문자열)로 통일해 배치 키/로그/파싱 혼선을 줄인다.
        return (scope == null) ? "" : scope.trim();
    }

    private String defaultRunKey(ClearingBatchType batchType) {
        // Intraday는 외부 runKey가 없어도 실행 단위를 식별할 수 있도록 시간 버킷 runKey를 사용한다.
        if (batchType == ClearingBatchType.EOD) {
            return "EOD";
        }
        long epochSeconds = Instant.now().getEpochSecond();
        long bucketSeconds = INTRADAY_BUCKET_MINUTES * 60L;
        long bucketStart = (epochSeconds / bucketSeconds) * bucketSeconds;
        return "INTRADAY-" + bucketStart;
    }

}
