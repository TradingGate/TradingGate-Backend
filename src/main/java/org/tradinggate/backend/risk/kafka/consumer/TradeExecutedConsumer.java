package org.tradinggate.backend.risk.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.risk.kafka.dto.TradeExecutedEvent;
import org.tradinggate.backend.risk.service.orchestrator.TradeProcessingOrchestrator;

import java.math.BigDecimal;

/**
 * 체결 이벤트 Kafka Consumer (B 모듈 진입점)
 *
 * 역할:
 * - A 모듈의 trades.executed 토픽 구독
 * - TradeExecutedEvent → TradeProcessingOrchestrator
 * - Manual Commit (멱등성 보장)
 *
 * 흐름:
 * 1. Kafka에서 메시지 수신
 * 2. JSON → TradeExecutedEvent 변환
 * 3. Orchestrator 호출
 * 4. 성공 시 Commit, 실패 시 Retry/DLQ
 */
@Slf4j
@Component
@Profile("risk")
@RequiredArgsConstructor
public class TradeExecutedConsumer {

  private final TradeProcessingOrchestrator orchestrator;
  private final ObjectMapper objectMapper;

  /**
   * trades.executed 토픽 리스너
   *
   * @param message
   * @param partition
   * @param offset
   * @param ack
   */
  @KafkaListener(topics = "${tradinggate.topics.trades-executed:trades.executed}", groupId = "${spring.kafka.consumer.group-id:risk-consumer-group}", containerFactory = "kafkaListenerContainerFactory")
  public void consume(
      @Payload String message,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset,
      Acknowledgment ack) {

    log.info("📨 Kafka message received: partition={}, offset={}", partition, offset);
    log.debug("📨 Message: {}", message);

    try {
      // JSON → TradeExecutedEvent 변환
      TradeExecutedEvent event = objectMapper.readValue(message, TradeExecutedEvent.class);

      // 이벤트 검증
      validateEvent(event);

      // Orchestrator 호출 (핵심 비즈니스 로직)
      boolean success = orchestrator.processTrade(event);

      if (success) {
        // Manual Commit
        ack.acknowledge();
        log.info("✅ Trade processed and committed: tradeId={}, offset={}",
            event.getTradeId(), offset);
      } else {
        log.error("❌ Trade processing failed (no exception): tradeId={}",
            event.getTradeId());
        // Commit하지 않음 → 재시도
      }

    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      // JSON 파싱 에러 → DLQ 전송 후 Commit (무한 재시도 방지)
      log.error("❌ JSON parsing error: partition={}, offset={}, error={}",
          partition, offset, e.getMessage());
      log.error("❌ Invalid message: {}", message);

      // TODO: DLQ로 전송
      // dlqProducer.send("trades.executed.dlq", message);

      // 파싱 불가능한 메시지는 Commit (재시도 불필요)
      ack.acknowledge();

    } catch (IllegalArgumentException e) {
      // 검증 에러 → DLQ 전송 후 Commit
      log.error("❌ Validation error: partition={}, offset={}, error={}",
          partition, offset, e.getMessage());

      // TODO: DLQ로 전송
      // dlqProducer.send("trades.executed.dlq", message);

      // 검증 실패는 Commit (재시도 불필요)
      ack.acknowledge();

    } catch (Exception e) {
      // 비즈니스 로직 에러 → 재시도 (Commit 안 함)
      log.error("❌ Business logic error: partition={}, offset={}, error={}",
          partition, offset, e.getMessage(), e);

      // Commit하지 않음 → 다음 poll에서 재시도
      // 재시도 횟수 제한은 Kafka Consumer Config에서 설정
    }
  }

  /**
   * 이벤트 검증
   */
  private void validateEvent(TradeExecutedEvent event) {
    if (event.getTradeId() == null || event.getTradeId().isEmpty()) {
      throw new IllegalArgumentException("tradeId is required");
    }
    if (event.getAccountId() == null) {
      throw new IllegalArgumentException("accountId is required");
    }
    if (event.getSymbol() == null || event.getSymbol().isEmpty()) {
      throw new IllegalArgumentException("symbol is required");
    }
    if (event.getSide() == null || event.getSide().isEmpty()) {
      throw new IllegalArgumentException("side is required");
    }
    if (!("BUY".equalsIgnoreCase(event.getSide()) || "SELL".equalsIgnoreCase(event.getSide()))) {
      throw new IllegalArgumentException("side must be BUY or SELL");
    }
    if (event.getQuantity() == null || event.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (event.getPrice() == null || event.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("price must be positive");
    }
  }
}
