package org.tradinggate.backend.clearing.policy;

import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;

/**
 * 수동 트리거 정책.
 * - 사용자(운영자)가 실행을 요청했을 때 결과를 명확히 알려주기 위해 REJECT를 사용한다.
 * - FAILED 재시도는 '리셋 API' 같은 명시적 액션 후에만 허용하는 것을 전제로 한다.
 */
public class ManualClearingBatchTriggerPolicy implements ClearingBatchTriggerPolicy {

    @Override
    public ClearingTriggerDecision decide(ClearingBatchStatus status) {
        return switch (status) {
            case PENDING -> ClearingTriggerDecision.proceed();
            case RUNNING -> ClearingTriggerDecision.reject("already running");
            case SUCCESS -> ClearingTriggerDecision.reject("already succeeded");
            case FAILED -> ClearingTriggerDecision.reject("failed batch; reset required before retry");
        };
    }
}
