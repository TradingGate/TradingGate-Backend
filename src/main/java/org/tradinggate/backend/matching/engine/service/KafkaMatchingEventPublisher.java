package org.tradinggate.backend.matching.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.matching.engine.model.MatchFill;
import org.tradinggate.backend.matching.engine.model.OrderUpdate;
import org.tradinggate.backend.matching.engine.util.MatchingProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.tradinggate.backend.matching.engine.util.KafkaJsonUtil.buildMakerPayload;
import static org.tradinggate.backend.matching.engine.util.KafkaJsonUtil.buildTakerPayload;

@Log4j2
@Component
@Profile("worker")
@RequiredArgsConstructor
public class KafkaMatchingEventPublisher implements MatchingEventPublisher {

    private final KafkaMessageProducer kafkaMessageProducer;
    private final ObjectMapper objectMapper;
    private final MatchingProperties matchingProperties;

    @Override
    public void publishOrderUpdates(
            String symbol,
            List<OrderUpdate> updates,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset
    ) {
        if (updates == null || updates.isEmpty()) return;

        String topic = matchingProperties.getOrdersUpdatedTopic();

        for (OrderUpdate update : updates) {
            try {
                String key = String.valueOf(update.getAccountId());

                Map<String, Object> envelope = Map.of(
                        "sourceTopic", sourceTopic,
                        "sourcePartition", sourcePartition,
                        "sourceOffset", sourceOffset,
                        "symbol", symbol,
                        "body", update
                );

                String payload = objectMapper.writeValueAsString(envelope);

                kafkaMessageProducer.sendAndWait(topic, key, payload);

            } catch (Exception e) {
                //  swallow 금지
                throw new RuntimeException("Failed to publish OrderUpdate. symbol=" + symbol
                        + ", source=" + sourceTopic + "-" + sourcePartition + "@" + sourceOffset, e);
            }
        }
    }

    @Override
    public void publishMatchFills(
            String symbol,
            List<MatchFill> fills,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset
    ) {
        if (fills == null || fills.isEmpty()) return;

        String topic = matchingProperties.getTradesExecutedTopic();

        for (MatchFill fill : fills) {
            try {
                long matchId = fill.getMatchId();
                long tradeId = matchId;

                Instant execTime = Instant.ofEpochMilli(fill.getExecutedAtMillis());

                String takerEventId = nextTradeEventId();
                Map<String, Object> takerPayload = buildTakerPayload(
                        fill, tradeId, matchId, execTime,
                        String.valueOf(fill.getQuantity()),
                        String.valueOf(fill.getPrice()),
                        takerEventId
                );

                Map<String, Object> takerEnvelope = Map.of(
                        "sourceTopic", sourceTopic,
                        "sourcePartition", sourcePartition,
                        "sourceOffset", sourceOffset,
                        "symbol", symbol,
                        "side", "TAKER",
                        "body", takerPayload
                );

                kafkaMessageProducer.sendAndWait(topic, String.valueOf(fill.getTakerAccountId()), objectMapper.writeValueAsString(takerEnvelope));

                String makerEventId = nextTradeEventId();
                Map<String, Object> makerPayload = buildMakerPayload(
                        fill, tradeId, matchId, execTime,
                        String.valueOf(fill.getQuantity()),
                        String.valueOf(fill.getPrice()),
                        makerEventId
                );

                Map<String, Object> makerEnvelope = Map.of(
                        "sourceTopic", sourceTopic,
                        "sourcePartition", sourcePartition,
                        "sourceOffset", sourceOffset,
                        "symbol", symbol,
                        "side", "MAKER",
                        "body", makerPayload
                );

                kafkaMessageProducer.sendAndWait(topic, String.valueOf(fill.getMakerAccountId()), objectMapper.writeValueAsString(makerEnvelope));

            } catch (Exception e) {
                throw new RuntimeException("Failed to publish trades.executed. symbol=" + symbol
                        + ", source=" + sourceTopic + "-" + sourcePartition + "@" + sourceOffset, e);
            }
        }
    }

    private String nextTradeEventId() {
        return "tradeEventID-" + UUID.randomUUID();
    }
}
