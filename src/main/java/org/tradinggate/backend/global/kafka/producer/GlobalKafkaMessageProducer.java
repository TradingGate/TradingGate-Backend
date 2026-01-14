package org.tradinggate.backend.global.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalKafkaMessageProducer {
  private final KafkaTemplate<String, String> kafkaTemplate;

  public void sendMessage(String topic, String key, String message) {
    log.info("Sending message to Topic: {}, Key: {}, Message: {}", topic, key, message);
    kafkaTemplate.send(topic, key, message)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            log.error("Failed to send message to topic: {}", topic, ex);
          } else {
            log.debug("Message sent successfully to topic: {}", topic);
          }
        });
  }

  // Key 없이 보낼 때 사용하는 오버로딩 메서드 (선택 사항)
  public void sendMessage(String topic, String message) {
    this.sendMessage(topic, null, message);
  }
}
