package org.tradinggate.backend.global.outbox.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.global.outbox.domain.OutboxEvent;
import org.tradinggate.backend.global.kafka.producer.KafkaMessageProducer;
import org.tradinggate.backend.global.outbox.domain.OutboxProducerType;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class KafkaOutboxMessageSender {

    private final KafkaMessageProducer kafkaMessageProducer;
    private final ObjectMapper objectMapper;
    private final OutboxProperties outboxProperties;

    public void send(OutboxEvent event) throws Exception {
        OutboxProducerType producerType = event.getProducerType();
        String eventType = event.getEventType();

        String topic = outboxProperties.resolveTopic(producerType, eventType);
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException("Outbox topic mapping not found. producerType=" + producerType
                    + ", eventType=" + eventType + ", eventId=" + event.getId());
        }

        String key = event.getIdempotencyKey();

        String json = objectMapper.writeValueAsString(event.getPayload());
        kafkaMessageProducer.sendAndWait(topic, key, json);
    }
}
