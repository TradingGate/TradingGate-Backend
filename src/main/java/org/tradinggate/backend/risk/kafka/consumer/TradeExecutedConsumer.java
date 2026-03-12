package org.tradinggate.backend.risk.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

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

    log.info("Kafka message received: partition={}, offset={}", partition, offset);
    log.debug("Message: {}", message);

    try {
      TradeExecutedEvent event = parseEvent(message);

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

      ack.acknowledge();

    } catch (IllegalArgumentException e) {
      log.error("❌ Validation error: partition={}, offset={}, error={}",
          partition, offset, e.getMessage());

      ack.acknowledge();

    } catch (Exception e) {
      log.error("❌ Business logic error: partition={}, offset={}, error={}",
          partition, offset, e.getMessage(), e);

    }
  }

  private TradeExecutedEvent parseEvent(String message) throws com.fasterxml.jackson.core.JsonProcessingException {
    JsonNode root = objectMapper.readTree(message);

    if (root.hasNonNull("body")) {
      JsonNode body = root.get("body");
      return TradeExecutedEvent.builder()
          .tradeId(text(body, "tradeId"))
          .accountId(longValue(body, "userId"))
          .symbol(text(body, "symbol"))
          .side(text(body, "side"))
          .quantity(decimal(body, "execQuantity"))
          .price(decimal(body, "execPrice"))
          .fee(decimalOrDefault(body, "fee", BigDecimal.ZERO))
          .feeAsset(textOrNull(body, "feeAsset"))
          .executedAt(dateTime(body, "execTime"))
          .build();
    }

    return objectMapper.treeToValue(root, TradeExecutedEvent.class);
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

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private String textOrNull(JsonNode node, String field) {
    return text(node, field);
  }

  private Long longValue(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asLong();
  }

  private BigDecimal decimal(JsonNode node, String field) {
    String raw = text(node, field);
    return raw == null || raw.isBlank() ? null : new BigDecimal(raw);
  }

  private BigDecimal decimalOrDefault(JsonNode node, String field, BigDecimal defaultValue) {
    BigDecimal value = decimal(node, field);
    return value == null ? defaultValue : value;
  }

  private LocalDateTime dateTime(JsonNode node, String field) {
    String raw = text(node, field);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return OffsetDateTime.parse(raw).toLocalDateTime();
  }
}
