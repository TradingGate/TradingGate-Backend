package org.tradinggate.backend.trading.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.trading.service.TradingProjectionService;

@Slf4j
@Component
@Profile("projection")
@RequiredArgsConstructor
public class TradeExecutedProjectionConsumer {

  private final TradingProjectionService projectionService;

  @KafkaListener(
      topics = "${tradinggate.topics.trades-executed:trades.executed}",
      groupId = "${spring.kafka.consumer.group-id:trading-projection}",
      containerFactory = "kafkaListenerContainerFactory")
  public void consume(
      @Payload String message,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset,
      Acknowledgment ack) {

    try {
      projectionService.applyTradeExecuted(message);
      ack.acknowledge();
    } catch (IllegalArgumentException e) {
      log.error("Invalid trades.executed message: partition={}, offset={}, error={}",
          partition, offset, e.getMessage());
      ack.acknowledge();
    } catch (Exception e) {
      log.error("Failed to project trades.executed: partition={}, offset={}", partition, offset, e);
      throw e instanceof RuntimeException re ? re : new RuntimeException(e);
    }
  }
}
