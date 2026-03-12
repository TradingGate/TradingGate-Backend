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
public class OrderUpdatedProjectionConsumer {

  private final TradingProjectionService projectionService;

  @KafkaListener(
      topics = "${tradinggate.topics.orders-updated:orders.updated}",
      groupId = "${spring.kafka.consumer.group-id:trading-projection}",
      containerFactory = "kafkaListenerContainerFactory")
  public void consume(
      @Payload String message,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset,
      Acknowledgment ack) {

    try {
      projectionService.applyOrderUpdate(message);
      ack.acknowledge();
    } catch (IllegalArgumentException e) {
      log.error("Invalid orders.updated message: partition={}, offset={}, error={}",
          partition, offset, e.getMessage());
      ack.acknowledge();
    } catch (Exception e) {
      log.error("Failed to project orders.updated: partition={}, offset={}", partition, offset, e);
      throw e instanceof RuntimeException re ? re : new RuntimeException(e);
    }
  }
}
