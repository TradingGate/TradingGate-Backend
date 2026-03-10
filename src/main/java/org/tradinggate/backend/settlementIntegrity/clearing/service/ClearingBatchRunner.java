package org.tradinggate.backend.settlementIntegrity.clearing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingFailureCode;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.dto.ClearingComputationContext;
import org.tradinggate.backend.settlementIntegrity.clearing.dto.ClearingScopeSpec;
import org.tradinggate.backend.settlementIntegrity.clearing.policy.ClearingBatchTriggerPolicy;
import org.tradinggate.backend.settlementIntegrity.clearing.service.support.ClearingScopeSpecParser;
import org.tradinggate.backend.settlementIntegrity.clearing.policy.ClearingTriggerDecision;
import org.tradinggate.backend.settlementIntegrity.clearing.policy.e.ClearingTriggerDecisionType;
import org.tradinggate.backend.settlementIntegrity.clearing.service.port.ClearingCalculator;

import java.time.LocalDate;

@Log4j2
@Service
@RequiredArgsConstructor
@Profile("clearing")
public class ClearingBatchRunner {

    private final ClearingBatchService clearingBatchService;

    private final ClearingResultWriter clearingResultWriter;
    private final ClearingOutboxService clearingOutboxService;
    private final ClearingCalculator clearingCalculator;

    private final ClearingScopeSpecParser scopeParser;

    /**
     * 배치 1회 실행 흐름:
     * 생성/조회 -> 정책 판단 -> 워터마크 선점 -> 계산/저장 -> 아웃박스 적재 -> 성공/실패 마킹
     */
    public void run(LocalDate businessDate, ClearingBatchType batchType, String scope, ClearingBatchTriggerPolicy policy) {
        ClearingBatch batch = clearingBatchService.getOrCreatePending(businessDate, batchType, scope);

        ClearingBatchStatus status = batch.getStatus();
        ClearingTriggerDecision decision = policy.decide(status);

        if (decision.type() == ClearingTriggerDecisionType.SKIP) {
            log.info("[CLEARING] skipped by policy. batchId={} status={} reason={}", batch.getId(), status, decision.reason());
            return;
        }
        if (decision.type() == ClearingTriggerDecisionType.REJECT) {
            ClearingFailureCode code = mapRejectToFailureCode(status);
            log.info("[CLEARING] rejected by policy. batchId={} status={} code={} reason={}",
                    batch.getId(), status, code.getCode(), decision.reason());
            return;
        }

        boolean acquired;
        try {
            acquired = clearingBatchService.tryMarkRunningWithDbWatermark(batch.getId());
        } catch (Exception e) {
            clearingBatchService.markFailed(batch.getId(), ClearingFailureCode.WATERMARK_RESOLVE_FAILED, summarize(e));
            throw e;
        }
        if (!acquired) {
            log.info("[CLEARING] not acquired. batchId={} code={}", batch.getId(), ClearingFailureCode.BATCH_NOT_ACQUIRED.getCode());
            return;
        }

        batch = clearingBatchService.findById(batch.getId());

        try {
            // scope 파싱 실패도 배치를 FAILED로 종료해야 재시도/운영 판단이 가능하다.
            ClearingScopeSpec spec = scopeParser.parse(batch.getScope());
            ClearingComputationContext ctx = ClearingComputationContext.from(batch, spec);
            clearingResultWriter.upsertResults(batch, clearingCalculator.calculate(ctx));
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
