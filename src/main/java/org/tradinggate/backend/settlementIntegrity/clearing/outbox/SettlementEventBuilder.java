package org.tradinggate.backend.settlementIntegrity.clearing.outbox;


import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingResult;

import java.util.Map;

public interface SettlementEventBuilder {

    /**
     * @param batch  정산 배치
     * @param result 정산 결과 row
     * @return OutboxEvent.payload (jsonb)
     */
    Map<String, Object> build(ClearingBatch batch, ClearingResult result);
}