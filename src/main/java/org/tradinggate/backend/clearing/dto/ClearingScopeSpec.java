package org.tradinggate.backend.clearing.dto;

import java.util.List;

public record ClearingScopeSpec(
        ScopeType type,
        AccountRange accountRange,
        List<Long> symbolIds,
        Chunk chunk
) {

    public enum ScopeType {
        ALL, ACCOUNT_RANGE, SYMBOL_SET, CHUNK
    }

    public record AccountRange(long fromInclusive, long toInclusive) {}

    public record Chunk(int index, int total) {
        public Chunk {
            if (total <= 0) throw new IllegalArgumentException("chunk total must be positive. total=" + total);
            if (index < 0 || index >= total) {
                throw new IllegalArgumentException("chunk index out of range. index=" + index + ", total=" + total);
            }
        }
    }

    public static ClearingScopeSpec all() {
        return new ClearingScopeSpec(ScopeType.ALL, null, null, null);
    }

    public static ClearingScopeSpec accountRange(long fromInclusive, long toInclusive) {
        if (fromInclusive <= 0 || toInclusive <= 0 || fromInclusive > toInclusive) {
            throw new IllegalArgumentException("invalid account range. from=" + fromInclusive + ", to=" + toInclusive);
        }
        return new ClearingScopeSpec(ScopeType.ACCOUNT_RANGE, new AccountRange(fromInclusive, toInclusive), null, null);
    }

    public static ClearingScopeSpec symbolSet(List<Long> symbolIds) {
        if (symbolIds == null || symbolIds.isEmpty()) {
            throw new IllegalArgumentException("symbol set is empty.");
        }
        return new ClearingScopeSpec(ScopeType.SYMBOL_SET, null, List.copyOf(symbolIds), null);
    }

    public static ClearingScopeSpec chunk(int index, int total) {
        return new ClearingScopeSpec(ScopeType.CHUNK, null, null, new Chunk(index, total));
    }
}
