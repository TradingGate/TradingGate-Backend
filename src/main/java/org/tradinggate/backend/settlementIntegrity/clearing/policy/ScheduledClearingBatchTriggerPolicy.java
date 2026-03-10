package org.tradinggate.backend.settlementIntegrity.clearing.policy;

import org.springframework.stereotype.Component;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;

/**
 * 자동 스케줄 트리거 정책.
 * - 스케줄은 중복 호출 가능성이 높아 "이미 처리됨/실행 중"을 에러로 취급하지 않는다.
 * - 성공/실패/실행중인 배치는 조용히 SKIP 한다.
 */
@Component
public class ScheduledClearingBatchTriggerPolicy implements ClearingBatchTriggerPolicy {

    @Override
    public ClearingTriggerDecision decide(ClearingBatchStatus status) {
        return switch (status) {
            case PENDING -> ClearingTriggerDecision.proceed();
            case RUNNING -> ClearingTriggerDecision.skip("already running");
            case SUCCESS -> ClearingTriggerDecision.skip("already succeeded");
            case FAILED -> ClearingTriggerDecision.skip("failed batch requires manual retry");
        };
    }
}
