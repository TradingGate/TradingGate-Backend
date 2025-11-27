package org.tradinggate.backend.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class KafkaMessageProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload);
        } catch (Exception e) {
            // TODO: DLQ / 모니터링 / 알림 등 공통 처리 여기로 모을 수 있음
            log.error("Failed to send Kafka message. topic={}, key={}, payload={}", topic, key, payload, e);
        }
    }
}
