package org.tradinggate.backend.risk.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.risk.service.TradeExecutionOrchestrator;
import org.tradinggate.backend.risk.repository.ProcessedTradeRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskEventListener {

  private final TradeExecutionOrchestrator orchestrator;
  private final ProcessedTradeRepository processedTradeRepository;
  private final ObjectMapper objectMapper;

  /**
   * trades.executed 카프카 메시지 처리
   */
  @KafkaListener(
      topics = "trades.executed",
      groupId = "risk-engine",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void handleTradeExecutedFromKafka(String message) {
    log.info("Received trades.executed from Kafka: {}", message);

    try {
      TradeExecutedEvent event = objectMapper.readValue(message, TradeExecutedEvent.class);

      // 멱등성 체크
      if (processedTradeRepository.existsByTradeId(event.getTradeId())) {
        log.warn("Trade already processed, skipping: tradeId={}", event.getTradeId());
        return;
      }

      //전체 플로우 실행
      orchestrator.executeTradeFlow(event);

      log.info("Trade processed successfully: tradeId={}, accountId={}",
          event.getTradeId(), event.getUserId());

    } catch (Exception e) {
      log.error("Error processing trades.executed message: {}", message, e);
      throw new RuntimeException("Failed to process trade execution", e);
    }
  }
}
