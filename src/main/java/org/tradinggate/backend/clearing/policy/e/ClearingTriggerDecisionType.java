package org.tradinggate.backend.clearing.policy.e;

public enum ClearingTriggerDecisionType {
    PROCEED,   // 이번 실행자가 배치를 수행해도 됨
    SKIP,      // 조용히 종료(자동 스케줄에 주로 사용)
    REJECT     // 호출자에게 명확히 거절(수동 트리거/API에 주로 사용)
}
