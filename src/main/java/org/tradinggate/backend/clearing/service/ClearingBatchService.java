package org.tradinggate.backend.clearing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.e.ClearingFailureCode;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.clearing.service.port.ClearingBatchContextProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.tradinggate.backend.clearing.service.port.ClearingBatchContextProvider.*;

@Service
@RequiredArgsConstructor
@Transactional
@Profile("clearing")
public class ClearingBatchService {

    private final ClearingBatchRepository clearingBatchRepository;

    private static final int INTRADAY_BUCKET_MINUTES = 10;

    /**
     * ClearingBatch 실행 단위를 관리
     * - (businessDate, batchType, runKey, attempt) 유니크를 기준으로 배치 중복 생성을 방지
     * - RUNNING 선점은 "UPDATE ... WHERE status=PENDING"으로 원자적으로 처리
     */
    public ClearingBatch getOrCreatePending(LocalDate businessDate, ClearingBatchType batchType, String scope) {
        String runKey = defaultRunKey(batchType);
        return getOrCreatePending(businessDate, batchType, runKey, 1, normalizeScope(scope));
    }

    public ClearingBatch getOrCreatePending(LocalDate businessDate, ClearingBatchType batchType, String runKey, int attempt, String scope) {
        String normalizedScope = normalizeScope(scope);
        return clearingBatchRepository.findByBusinessDateAndBatchTypeAndRunKeyAndAttempt(businessDate, batchType, runKey, attempt)
                .orElseGet(() -> createPendingWithUniqGuard(businessDate, batchType, runKey, attempt, normalizedScope));
    }

    /**
     * 배치를 RUNNING으로 선점
     *
     * @param batchId 선점 대상 배치 ID
     * @param batchContext/cutoffOffsets Kafka partition별 cutoff offset 맵 (정합성 기준점)
     * @param batchContext/marketSnapshotId 평가 가격 기준 스냅샷 ID (정합성 기준점)
     * @return 선점 성공 여부 (true면 이번 실행자가 배치를 수행)
     * @sideEffect 성공 시 배치 상태/startedAt/cutoffOffsets/marketSnapshotId가 변경된다.
     */
    public boolean tryMarkRunning(Long batchId, ClearingBatchContext batchContext) {
        Instant now = Instant.now();

        int updated = clearingBatchRepository.tryMarkRunning(
                batchId,
                ClearingBatchStatus.PENDING,
                ClearingBatchStatus.RUNNING,
                now,
                requireNonNullCutoffOffsets(batchId, batchContext),
                batchContext.marketSnapshotId()
        );

        return updated == 1;
    }

    public ClearingBatch createRetryBatch(Long failedBatchId) {
        ClearingBatch prev = clearingBatchRepository.findById(failedBatchId)
                .orElseThrow(() -> new IllegalStateException("ClearingBatch not found. batchId=" + failedBatchId));

        if (prev.getStatus() != ClearingBatchStatus.FAILED) {
            return prev;
        }

        int nextAttempt = prev.getAttempt() + 1;
        ClearingBatch retry = ClearingBatch.builder()
                .businessDate(prev.getBusinessDate())
                .batchType(prev.getBatchType())
                .runKey(prev.getRunKey())
                .attempt(nextAttempt)
                .retryOfBatchId(prev.getId())
                .scope(normalizeScope(prev.getScope()))
                .status(ClearingBatchStatus.PENDING)
                .cutoffOffsets(Map.of())
                .build();

        return clearingBatchRepository.saveAndFlush(retry);
    }

    public void markSuccess(Long batchID) {
        clearingBatchRepository.markSuccess(batchID, ClearingBatchStatus.SUCCESS, Instant.now());
    }

    public void markFailed(Long batchId, ClearingFailureCode failureCode, String detail) {
        String remark = formatRemark(failureCode, detail);
        clearingBatchRepository.markFailed(batchId, ClearingBatchStatus.FAILED, Instant.now(), remark);
    }

    private ClearingBatch createPendingWithUniqGuard(LocalDate businessDate, ClearingBatchType batchType, String runKey, int attempt, String scope) {
        try {
            ClearingBatch batch = ClearingBatch.pending(businessDate, batchType, runKey, attempt, normalizeScope(scope));
            return clearingBatchRepository.saveAndFlush(batch);
        } catch (DataIntegrityViolationException e) {
            //동시 생성 경쟁에서 유니크 제약에 걸린 경우, "이미 누군가 생성"한 것을 조회해 재사용한다.
            return clearingBatchRepository.findByBusinessDateAndBatchTypeAndRunKeyAndAttempt(businessDate, batchType, runKey, attempt).orElseThrow(() -> e);
        }
    }

    private String formatRemark(ClearingFailureCode code, String detail) {
        // remark에 운영 분류 코드가 반드시 남아야 사후 집계/대응이 가능하다.
        String safeDetail = detail == null ? "" : detail;
        String raw = code.getCode() + "|" + safeDetail;
        return raw.length() <= 255 ? raw : raw.substring(0, 255);
    }

    private Map<String, Long> requireNonNullCutoffOffsets(Long batchId, ClearingBatchContext batchContext) {
        // 왜: cutoffOffsets는 RUNNING 진입 시 확정되어야 하며, null이면 정합성(NFR-C-01) 검증이 불가능하다.
        if (batchContext.cutoffOffsets() == null) {
            throw new IllegalStateException("cutoffOffsets is null. batchId=" + batchId);
        }
        return batchContext.cutoffOffsets();
    }

    private String normalizeScope(String scope) {
        // 왜: scope는 null/blank를 ALL로 해석하지만, 표현을 통일해야 파싱/로그/키 생성에서 혼선을 줄일 수 있다.
        return (scope == null) ? "" : scope.trim();
    }

    private String defaultRunKey(ClearingBatchType batchType) {
        // 외부 트리거 입력이 없는 경우에도 Intraday 배치를 실행 단위로 기록하기 위해 시간 버킷 기반 runKey를 사용한다.
        if (batchType == ClearingBatchType.EOD) {
            return "EOD";
        }
        long epochSeconds = Instant.now().getEpochSecond();
        long bucketSeconds = INTRADAY_BUCKET_MINUTES * 60L;
        long bucketStart = (epochSeconds / bucketSeconds) * bucketSeconds;
        return "INTRADAY-" + bucketStart;
    }
}
