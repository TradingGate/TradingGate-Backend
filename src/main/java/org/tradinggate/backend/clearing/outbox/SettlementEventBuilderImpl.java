package org.tradinggate.backend.clearing.outbox;

import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.ClearingResult;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SettlementEventBuilderImpl implements SettlementEventBuilder {

    private static final int SCHEMA_VERSION = 1;
    private static final String TOPIC = "clearing.settlement";
    private static final String EVENT_TYPE = "CLEARING.SETTLEMENT";

    /**
     * 왜: payload는 공통 outbox 규약(topic/key/body)으로 고정해 downstream 소비자가 일관되게 처리할 수 있게 한다.
     */
    @Override
    public Map<String, Object> build(ClearingBatch batch, ClearingResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", EVENT_TYPE);
        body.put("businessDate", batch.getBusinessDate().toString());
        body.put("batchId", batch.getId());
        body.put("batchType", batch.getBatchType().name());
        body.put("marketSnapshotId", batch.getMarketSnapshotId());

        body.put("accountId", result.getAccountId());
        body.put("symbolId", result.getSymbolId());
        body.put("status", result.getStatus().name());

        body.put("openingQty", result.getOpeningQty());
        body.put("closingQty", result.getClosingQty());
        body.put("openingPrice", result.getOpeningPrice());
        body.put("closingPrice", result.getClosingPrice());

        body.put("realizedPnl", result.getRealizedPnl());
        body.put("unrealizedPnl", result.getUnrealizedPnl());
        body.put("fee", result.getFee());
        body.put("funding", result.getFunding());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("topic", TOPIC);
        payload.put("key", String.valueOf(result.getAccountId()));
        payload.put("producer", "CLEARING");
        payload.put("eventType", EVENT_TYPE);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("body", body);

        return payload;
    }
}
