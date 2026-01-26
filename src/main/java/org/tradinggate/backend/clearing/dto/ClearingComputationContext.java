package org.tradinggate.backend.clearing.dto;

import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;

import java.time.LocalDate;
import java.util.Map;

public record ClearingComputationContext(
        Long batchId,
        LocalDate businessDate,
        ClearingBatchType batchType,
        String scopeRaw,
        ClearingScopeSpec scopeSpec,
        Map<String, Long> cutoffOffsets,
        Long marketSnapshotId
) {
    public static ClearingComputationContext from(
            ClearingBatch batch,
            ClearingScopeSpec scopeSpec
    ) {
        return new ClearingComputationContext(
                batch.getId(),
                batch.getBusinessDate(),
                batch.getBatchType(),
                batch.getScope(),
                scopeSpec,
                batch.getCutoffOffsets(),
                batch.getMarketSnapshotId()
        );
    }
}