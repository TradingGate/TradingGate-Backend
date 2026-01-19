package org.tradinggate.backend.clearing.policy;

import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;

public interface ClearingBatchTriggerPolicy {

    /**
     * 배치 현재 상태를 기준으로 이번 트리거가 어떤 동작을 해야 하는지 결정한다.
     *
     * @param status 현재 배치 상태
     * @return 정책 결정(PROCEED/SKIP/REJECT)
     */
    ClearingTriggerDecision decide(ClearingBatchStatus status);
}
