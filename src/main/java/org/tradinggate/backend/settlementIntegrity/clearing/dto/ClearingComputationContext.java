package org.tradinggate.backend.settlementIntegrity.clearing.dto;

import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;

import java.time.LocalDate;
import java.util.Map;

public record ClearingComputationContext(
        Long batchId,
        String snapshotKey,
        LocalDate businessDate,
        ClearingBatchType batchType,
        Map<String, Long> cutoffOffsets,
        ClearingScopeSpec scopeSpec
) {
    public static ClearingComputationContext from(ClearingBatch batch, ClearingScopeSpec spec) {
        return new ClearingComputationContext(
                batch.getId(),
                batch.getSnapshotKey(),
                batch.getBusinessDate(),
                batch.getBatchType(),
                batch.getCutoffOffsets(),
                spec
        );
    }
}