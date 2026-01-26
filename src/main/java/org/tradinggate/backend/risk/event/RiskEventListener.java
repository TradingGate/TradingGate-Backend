package org.tradinggate.backend.risk.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.risk.service.PositionService;
import org.tradinggate.backend.risk.repository.ProcessedTradeRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskEventListener {

  private final PositionService positionService;
  private final ProcessedTradeRepository processedTradeRepository;
  private final ObjectMapper objectMapper;

  /**
   * B-1: trades.executed 카프카 메시지 처리
   * - 멱등성 보장 (tradeId 중복 체크)
   * - 포지션 업데이트
   * - PnL 계산
   * - accountbalance 업데이트
   * - pnlintraday 저장
   */
  @KafkaListener(
      topics = "trades.executed",
      groupId = "risk-engine",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void handleTradeExecutedFromKafka(String message) {
    log.info("Received trades.executed from Kafka: {}", message);

    try {
      // JSON 파싱하여 TradeExecutedEvent로 변환
      TradeExecutedEvent event = objectMapper.readValue(message, TradeExecutedEvent.class);

      // 멱등성 체크: 이미 처리된 tradeId인지 확인
      if (processedTradeRepository.existsByTradeId(event.getTradeId())) {
        log.warn("Trade already processed, skipping: tradeId={}", event.getTradeId());
        return; // 중복 처리 방지
      }

      // B-1: 포지션 & PnL 업데이트 + DB 저장
      positionService.processTradeExecution(event);

      log.info("Trade processed successfully: tradeId={}, accountId={}",
          event.getTradeId(), event.getUserId());

    } catch (Exception e) {
      log.error("Error processing trades.executed message: {}", message, e);
      // DLQ로 전송 또는 재시도 로직
      throw new RuntimeException("Failed to process trade execution", e);
    }
  }
}
