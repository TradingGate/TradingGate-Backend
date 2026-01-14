package org.tradinggate.backend.trading.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Kafka 메시지 발행 공통 래퍼
 * - KafkaTemplate을 직접 노출하지 않음
 * - 동기 전송 (get()으로 결과 대기)
 * - Retry 로직 내장 (200ms → 400ms → 800ms)
 */
@Component("tradingKafkaMessageProducer")
@RequiredArgsConstructor
@Log4j2
public class KafkaMessageProducer {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  /**
   * 메시지 발행 (동기 방식, 재시도 3회)
   *
   * @param topic 토픽명
   * @param key 파티션 키
   * @param event 발행할 이벤트 객체
   */
  @Retryable(
      retryFor = Exception.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 2000)
  )
  public void sendMessage(String topic, String key, Object event) {
    try {
      String message = objectMapper.writeValueAsString(event);

      // 동기 전송 (결과 대기)
      var result = kafkaTemplate.send(topic, key, message).get();

      log.info("[Kafka] 메시지 발행 성공: topic={}, partition={}, offset={}, key={}",
          topic,
          result.getRecordMetadata().partition(),
          result.getRecordMetadata().offset(),
          key);

    } catch (Exception e) {
      log.error("[Kafka] 메시지 발행 실패: topic={}, key={}", topic, key, e);
      throw new RuntimeException("Kafka send failed", e);
    }
  }

  /**
   * 재시도 3회 모두 실패 시 호출
   */
  @Recover
  public void recover(Exception e, String topic, String key, Object event) {
    log.error("[KAFKA] send failed after retries topic={}, key={}, event={}",
        topic, key, event.getClass().getSimpleName(), e);

    // TODO: Dead Letter Queue(DLQ)로 전송하거나 DB에 저장
    // deadLetterService.save(topic, key, event, e.getMessage());

    throw new RuntimeException("Kafka send failed after retries", e);
  }
}
