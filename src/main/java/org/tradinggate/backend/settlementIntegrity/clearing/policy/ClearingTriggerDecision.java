package org.tradinggate.backend.settlementIntegrity.clearing.policy;

import org.tradinggate.backend.settlementIntegrity.clearing.policy.e.ClearingTriggerDecisionType;

public record ClearingTriggerDecision(ClearingTriggerDecisionType type, String reason) {
    public static ClearingTriggerDecision proceed() {
        return new ClearingTriggerDecision(ClearingTriggerDecisionType.PROCEED, null);
    }

    public static ClearingTriggerDecision skip(String reason) {
        return new ClearingTriggerDecision(ClearingTriggerDecisionType.SKIP, reason);
    }

    public static ClearingTriggerDecision reject(String reason) {
        return new ClearingTriggerDecision(ClearingTriggerDecisionType.REJECT, reason);
    }
}
