package org.tradinggate.backend.matching.snapshot.dto;

import java.util.List;

public record PartitionCleanupResult(
        String topic,
        int partition,
        String reason,
        List<String> removedSymbols
) {
    public static PartitionCleanupResult none(String reason) {
        return new PartitionCleanupResult("", -1, reason, List.of());
    }

    public int removedCount() {
        return removedSymbols == null ? 0 : removedSymbols.size();
    }
}
