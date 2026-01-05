package org.tradinggate.backend.global.outbox.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.global.outbox.domain.OutboxEvent;
import org.tradinggate.backend.matching.engine.service.KafkaMessageProducer;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class KafkaOutboxMessageSender {

    private final KafkaMessageProducer kafkaMessageProducer;
    private final ObjectMapper objectMapper;

    public void send(OutboxEvent event) throws Exception {
        Map<String, Object> payload = event.getPayload();

        Object topicObj = payload.get("topic");
        Object keyObj = payload.get("key");

        if (!(topicObj instanceof String topic) || topic.isBlank()) {
            throw new IllegalArgumentException("Outbox payload must include non-empty 'topic'. eventId=" + event.getId());
        }
        if (!(keyObj instanceof String key) || key.isBlank()) {
            throw new IllegalArgumentException("Outbox payload must include non-empty 'key'. eventId=" + event.getId());
        }

        String json = objectMapper.writeValueAsString(payload);
        kafkaMessageProducer.sendAndWait(topic, key, json);
    }
}
