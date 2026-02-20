package org.tradinggate.backend.clearing.outbox;

import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.ClearingResult;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SettlementEventBuilderImpl implements SettlementEventBuilder {

    private static final int SCHEMA_VERSION = 2;
    private static final String TOPIC = "clearing.settlement";
    private static final String EVENT_TYPE = "CLEARING.SETTLEMENT";

    @Override
    public Map<String, Object> build(ClearingBatch batch, ClearingResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", EVENT_TYPE);
        body.put("businessDate", batch.getBusinessDate().toString());
        body.put("batchId", batch.getId());
        body.put("batchType", batch.getBatchType().name());

        body.put("accountId", result.getAccountId());
        body.put("asset", result.getAsset());
        body.put("status", result.getStatus().name());

        body.put("openingBalance", result.getOpeningBalance());
        body.put("closingBalance", result.getClosingBalance());
        body.put("netChange", result.getNetChange());

        body.put("feeTotal", result.getFeeTotal());
        body.put("tradeCount", result.getTradeCount());
        body.put("tradeValue", result.getTradeValue());
        body.put("snapshotKey", batch.getSnapshotKey());
        body.put("watermarkOffsets", batch.getCutoffOffsets());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("topic", TOPIC);
        payload.put("key", String.valueOf(result.getAccountId()));
        payload.put("producer", "CLEARING");
        payload.put("eventType", EVENT_TYPE);
        Instant occurredAt = batch.getStartedAt() != null ? batch.getStartedAt() : Instant.now();
        payload.put("occurredAt", occurredAt.toString());
        payload.put("body", body);

        return payload;
    }
}
