package org.tradinggate.backend.trading.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.SourceType;
import org.tradinggate.backend.trading.kafka.event.OrderCancelEvent;
import org.tradinggate.backend.trading.kafka.event.OrderCreateEvent;

/**
 * 주문 이벤트 발행자 (PDF 기준)
 * - orders.in: 신규 주문, 주문 취소 (commandType으로 구분)
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class OrderEventProducer {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final SourceType sourceType;

  private static final String ORDERS_IN_TOPIC = "orders.in";

  /**
   * 신규 주문 이벤트 발행 (commandType: NEW)
   */
  public void publishNewOrder(Order order) {
    try {
      OrderCreateEvent event = OrderCreateEvent.from(order, sourceType);
      String message = objectMapper.writeValueAsString(event);

      // symbol 기준 파티셔닝
      kafkaTemplate
          .send(ORDERS_IN_TOPIC, Objects.requireNonNull(event.getPartitionKey()), Objects.requireNonNull(message))
          .whenComplete((result, ex) -> {
            if (ex != null) {
              log.error("주문 이벤트 발행 실패: clientOrderId={}, error={}",
                  order.getClientOrderId(), ex.getMessage(), ex);
            } else {
              log.info("주문 이벤트 발행 성공: clientOrderId={}, partition={}, commandType={}",
                  order.getClientOrderId(),
                  result.getRecordMetadata().partition(),
                  event.getCommandType());
            }
          });
    } catch (Exception e) {
      log.error("주문 이벤트 직렬화 실패: clientOrderId={}", order.getClientOrderId(), e);
      throw new RuntimeException("Kafka 이벤트 발행 실패", e);
    }
  }

  /**
   * 주문 취소 이벤트 발행 (commandType: CANCEL)
   */
  public void publishCancelOrder(Order order) {
    try {
      OrderCancelEvent event = OrderCancelEvent.from(order, sourceType);
      String message = objectMapper.writeValueAsString(event);

      // symbol 기준 파티셔닝
      kafkaTemplate
          .send(ORDERS_IN_TOPIC, Objects.requireNonNull(event.getPartitionKey()), Objects.requireNonNull(message))
          .whenComplete((result, ex) -> {
            if (ex != null) {
              log.error("주문 취소 이벤트 발행 실패: clientOrderId={}, error={}",
                  order.getClientOrderId(), ex.getMessage(), ex);
            } else {
              log.info("주문 취소 이벤트 발행 성공: clientOrderId={}, partition={}, commandType={}",
                  order.getClientOrderId(),
                  result.getRecordMetadata().partition(),
                  event.getCommandType());  // ✅ 동적으로 출력
            }
          });
    } catch (Exception e) {
      log.error("주문 취소 이벤트 직렬화 실패: clientOrderId={}", order.getClientOrderId(), e);
      throw new RuntimeException("Kafka 이벤트 발행 실패", e);
    }
  }
}
