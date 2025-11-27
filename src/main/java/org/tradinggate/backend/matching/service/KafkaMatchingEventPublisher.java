package org.tradinggate.backend.matching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.matching.domain.MatchFill;
import org.tradinggate.backend.matching.domain.OrderUpdate;
import org.tradinggate.backend.matching.util.MatchingProperties;

import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.tradinggate.backend.matching.util.KafkaJsonUtil.buildMakerPayload;
import static org.tradinggate.backend.matching.util.KafkaJsonUtil.buildTakerPayload;

@Log4j2
@Component
@Profile("worker")
@RequiredArgsConstructor
public class KafkaMatchingEventPublisher implements MatchingEventPublisher {

    private final KafkaMessageProducer kafkaMessageProducer;
    private final ObjectMapper objectMapper;
    private final MatchingProperties matchingProperties;

    @Override
    public void publishOrderUpdates(String symbol, List<OrderUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        String topic = matchingProperties.getOrdersUpdatedTopic();

        for (OrderUpdate update : updates) {
            try {
                String key = String.valueOf(update.getAccountId());

                String payload = objectMapper.writeValueAsString(update);

                kafkaMessageProducer.send(topic, key, payload);

                if (log.isDebugEnabled()) {
                    log.debug("Published OrderUpdate: topic={}, key={}, update={}", topic, key, update);
                }

            } catch (JsonProcessingException e) {
                log.error("Failed to serialize OrderUpdate. update={}", update, e);
            } catch (Exception e) {
                log.error("Failed to send OrderUpdate to Kafka. update={}", update, e);
            }
        }
    }

    @Override
    public void publishMatchFills(String symbol, List<MatchFill> fills) {
        if (fills == null || fills.isEmpty()) {
            return;
        }

        String topic = matchingProperties.getTradesExecutedTopic();

        for (MatchFill fill : fills) {
            // v1: tradeId = matchId 재사용 (필요 시 별도 시퀀스로 확장 가능)
            long matchId = fill.getMatchId();
            long tradeId = matchId;

            Instant execTime = Instant.ofEpochMilli(fill.getExecutedAtMillis());

            String execQtyStr = String.valueOf(fill.getQuantity());
            String execPriceStr = String.valueOf(fill.getPrice());

            String takerEventId = nextTradeEventId();
            Map<String, Object> takerPayload = buildTakerPayload(
                    fill, tradeId, matchId, execTime, execQtyStr, execPriceStr, takerEventId
            );

            sendTradesExecuted(topic, String.valueOf(fill.getTakerAccountId()), takerPayload);

            String makerEventId = nextTradeEventId();
            Map<String, Object> makerPayload = buildMakerPayload(
                    fill, tradeId, matchId, execTime, execQtyStr, execPriceStr, makerEventId
            );

            sendTradesExecuted(topic, String.valueOf(fill.getMakerAccountId()), makerPayload);
        }
    }

    private void sendTradesExecuted(String topic, String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaMessageProducer.send(topic, key, json);

            if (log.isDebugEnabled()) {
                log.debug("Published trades.executed: topic={}, key={}, payload={}", topic, key, json);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize trades.executed payload. key={}, payload={}", key, payload, e);
        } catch (Exception e) {
            log.error("Failed to send trades.executed to Kafka. key={}, payload={}", key, payload, e);
        }
    }

    private String nextTradeEventId() {
        return "tradeEventID-" + UUID.randomUUID();
    }
}
