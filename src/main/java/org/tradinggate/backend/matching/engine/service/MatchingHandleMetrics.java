package org.tradinggate.backend.matching.engine.service;

import org.tradinggate.backend.matching.engine.model.MatchResult;

public record MatchingHandleMetrics(
        MatchResult result,
        long consumedAtMillis,
        long engineCompletedAtMillis,
        long publishCompletedAtMillis
) {
    public long engineDurationMillis() {
        return engineCompletedAtMillis - consumedAtMillis;
    }

    public long publishDurationMillis() {
        return publishCompletedAtMillis - engineCompletedAtMillis;
    }

    public long totalHandleDurationMillis() {
        return publishCompletedAtMillis - consumedAtMillis;
    }
}
