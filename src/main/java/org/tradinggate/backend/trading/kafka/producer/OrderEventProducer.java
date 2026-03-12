package org.tradinggate.backend.trading.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.global.exception.CustomException;
import org.tradinggate.backend.global.exception.TradingErrorCode;
import org.tradinggate.backend.global.kafka.producer.KafkaMessageProducer;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.SourceType;
import org.tradinggate.backend.trading.kafka.event.OrderCancelEvent;
import org.tradinggate.backend.trading.kafka.event.OrderCreateEvent;

/**
 * 주문 이벤트 발행자
 * - KafkaMessageProducer를 통해 간접 발행
 * - Retry 로직은 KafkaMessageProducer에서 처리
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class OrderEventProducer {

  private final KafkaMessageProducer kafkaMessageProducer;
//  private final SourceType sourceType;
  private final ObjectMapper objectMapper;

  private static final String ORDERS_IN_TOPIC = "orders.in";

  @Value("${tradinggate.kafka.api-send-timeout-ms:3000}")
  private long apiSendTimeoutMs;

  /**
   * 신규 주문 이벤트 발행 (commandType: NEW)
   */
  public void publishNewOrder(Order order) {
    log.info("[OrderEventProducer] 신규 주문 발행 시작: clientOrderId={}", order.getClientOrderId());

    try {
      OrderCreateEvent event = OrderCreateEvent.from(order, SourceType.API);
      String jsonPayload = objectMapper.writeValueAsString(event);
      kafkaMessageProducer.sendAndWaitOnce(ORDERS_IN_TOPIC, event.getPartitionKey(), jsonPayload, apiSendTimeoutMs);

      log.info("[OrderEventProducer] 신규 주문 발행 성공: clientOrderId={}, commandType={}",
          order.getClientOrderId(), event.getCommandType());

    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("[OrderEventProducer] 신규 주문 발행 실패: clientOrderId={}",
          order.getClientOrderId(), e);
      throw new CustomException(TradingErrorCode.MESSAGE_BROKER_UNAVAILABLE);
    }
  }

  /**
   * 주문 취소 이벤트 발행 (commandType: CANCEL)
   */
  public void publishCancelOrder(Order order) {
    log.info("[OrderEventProducer] 주문 취소 발행 시작: clientOrderId={}", order.getClientOrderId());

    try {
      OrderCancelEvent event = OrderCancelEvent.from(order, SourceType.API);
      String jsonPayload = objectMapper.writeValueAsString(event);
      kafkaMessageProducer.sendAndWaitOnce(ORDERS_IN_TOPIC, event.getPartitionKey(), jsonPayload, apiSendTimeoutMs);

      log.info("[OrderEventProducer] 주문 취소 발행 성공: clientOrderId={}, commandType={}",
          order.getClientOrderId(), event.getCommandType());

    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("[OrderEventProducer] 주문 취소 발행 실패: clientOrderId={}",
          order.getClientOrderId(), e);
      throw new CustomException(TradingErrorCode.MESSAGE_BROKER_UNAVAILABLE);
    }
  }
}
