package org.tradinggate.backend.global.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class KafkaMessageProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 2000)
    )

    public void sendAndWait(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get();

        } catch (Exception e) {
            log.error("Failed to send Kafka message. topic={}, key={}", topic, key, e);
            throw new RuntimeException("Kafka send failed", e);
        }
    }

    @Recover
    public void recover(Exception e, String topic, String key, String payload) {
        log.error("[KAFKA] send failed after retries topic={}, key={}", topic, key, e);
        throw new RuntimeException("Kafka send failed after retries", e);
    }
}
