package org.tradinggate.backend.clearing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.e.ClearingFailureCode;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.policy.ClearingBatchTriggerPolicy;
import org.tradinggate.backend.clearing.policy.ClearingTriggerDecision;
import org.tradinggate.backend.clearing.policy.e.ClearingTriggerDecisionType;
import org.tradinggate.backend.clearing.service.port.ClearingBatchContextProvider;
import org.tradinggate.backend.clearing.service.port.ClearingBatchContextProvider.ClearingBatchContext;
import org.tradinggate.backend.clearing.service.port.ClearingCalculator;

import java.time.LocalDate;

@Log4j2
@Service
@RequiredArgsConstructor
@Profile("clearing")
public class ClearingBatchRunner {
    private final ClearingBatchService clearingBatchService;
    private final ClearingBatchContextProvider provider;

    private final ClearingResultWriter clearingResultWriter;
    private final ClearingOutboxService clearingOutboxService;
    private final ClearingCalculator clearingCalculator;

    /**
     * Clearing 배치 실행을 트리거한다.
     * - 배치 생성/조회 → RUNNING 선점 → (계산/저장/이벤트) → 성공/실패 마킹 순서로 수행한다.
     */
    public void run(LocalDate businessDate, ClearingBatchType batchType, String scope, ClearingBatchTriggerPolicy policy) {
        ClearingBatch batch = clearingBatchService.getOrCreatePending(businessDate, batchType, scope);

        ClearingBatchStatus status = batch.getStatus();

        ClearingTriggerDecision decision = policy.decide(status);
        if (decision.type() == ClearingTriggerDecisionType.SKIP) {
            //스케줄 중복 호출은 정상 케이스가 많아 노이즈를 줄이기 위해 조용히 종료한다.
            log.info("[CLEARING] skipped by policy. batchId={} status={} reason={}", batch.getId(), status, decision.reason());
            return;
        }

        if (decision.type() == ClearingTriggerDecisionType.REJECT) {
            ClearingFailureCode code = mapRejectToFailureCode(status);
            log.info("[CLEARING] rejected by policy. batchId={} status={} code={} reason={}",
                    batch.getId(), status, code.getCode(), decision.reason());
            return;
        }

        ClearingBatchContext clearingBatchContext;

        try {
            clearingBatchContext = provider.resolve(batchType, scope);
        } catch (Exception e) {
            clearingBatchService.markFailed(batch.getId(), ClearingFailureCode.CUTOFF_RESOLVE_FAILED, summarize(e));
            throw e;
        }

        boolean acquired = clearingBatchService.tryMarkRunning(batch.getId(), clearingBatchContext);
        if (!acquired) {
            log.info("[CLEARING] not acquired. batchId={} code={}", batch.getId(), ClearingFailureCode.BATCH_NOT_ACQUIRED.getCode());
            return;
        }

        try {
            // TODO(B-5): 계산/결과 저장 (CR-03/CR-04)
            clearingResultWriter.upsertResults(batch, clearingCalculator.calculate(batch));
            // TODO(B-5): outbox 이벤트 적재 (CR-07)
            clearingOutboxService.enqueueSettlementEvents(batch.getId());

            clearingBatchService.markSuccess(batch.getId());
        } catch (Exception e) {
            clearingBatchService.markFailed(batch.getId(), ClearingFailureCode.UNEXPECTED_ERROR, summarize(e));
            throw e;
        }
    }

    private ClearingFailureCode mapRejectToFailureCode(ClearingBatchStatus status) {
        return switch (status) {
            case RUNNING -> ClearingFailureCode.BATCH_ALREADY_RUNNING;
            case SUCCESS -> ClearingFailureCode.BATCH_ALREADY_SUCCEEDED;
            case FAILED -> ClearingFailureCode.FAILED_BATCH_REQUIRES_RESET;
            default -> ClearingFailureCode.UNEXPECTED_ERROR;
        };
    }

    private String summarize(Exception e) {
        String msg = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
        return msg.length() <= 255 ? msg : msg.substring(0, 255);
    }
}
