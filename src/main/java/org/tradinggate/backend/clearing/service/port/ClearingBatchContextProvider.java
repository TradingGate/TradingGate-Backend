package org.tradinggate.backend.clearing.service.port;

import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;

import java.util.Map;

public interface ClearingBatchContextProvider {

    /**
     * 왜: cutoffOffsets + marketSnapshotId는 배치 정합성의 기준점이며 RUNNING 전이 순간에 함께 확정되어야 한다.
     * 규칙: resolve(...) 결과는 배치 실행 동안 변하면 안 된다.
     */
    ClearingBatchContext resolve(ClearingBatchType batchType, String scope);

    record ClearingBatchContext(
            Map<String, Long> cutoffOffsets,
            Long marketSnapshotId
    ) {}
}
