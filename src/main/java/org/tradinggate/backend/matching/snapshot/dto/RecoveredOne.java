package org.tradinggate.backend.matching.snapshot.dto;

public record RecoveredOne(
        String symbol,
        long snapshotOffset,
        long startOffset,
        String snapshotId,
        String fileName
) {}
