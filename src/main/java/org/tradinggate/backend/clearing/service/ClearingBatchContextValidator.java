package org.tradinggate.backend.clearing.service;

import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.service.port.ClearingBatchContextProvider;

import java.util.Map;

@Component
public class ClearingBatchContextValidator {

    /**
     *  cutoffOffsets/snapshotId는 정산 기준점이며, null/비정상 값이면 정산 재현성이 깨진다.
     *  RUNNING 선점 전에 fail-fast로 막는다.
     */
    public void validate(Long batchId, ClearingBatchType batchType, String scope, ClearingBatchContextProvider.ClearingBatchContext ctx) {
        if (ctx == null) {
            throw new IllegalStateException("batchContext is null. batchId=" + batchId + ", type=" + batchType + ", scope=" + scope);
        }

        Map<String, Long> offsets = ctx.cutoffOffsets();
        if (offsets == null || offsets.isEmpty()) {
            throw new IllegalStateException("cutoffOffsets is empty. batchId=" + batchId + ", type=" + batchType + ", scope=" + scope);
        }

        // partition은 문자열로 관리(카프카 partition id를 문자열로 직렬화해서 jsonb 저장)
        for (var e : offsets.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                throw new IllegalStateException("cutoffOffsets contains blank partition. batchId=" + batchId);
            }
            if (e.getValue() == null || e.getValue() < 0) {
                throw new IllegalStateException("cutoffOffsets contains invalid offset. batchId=" + batchId
                        + ", partition=" + e.getKey() + ", offset=" + e.getValue());
            }
        }

        if (ctx.marketSnapshotId() == null || ctx.marketSnapshotId() <= 0) {
            throw new IllegalStateException("marketSnapshotId is invalid. batchId=" + batchId + ", value=" + ctx.marketSnapshotId());
        }
    }
}
