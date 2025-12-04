package org.tradinggate.backend.trading.kafka.producer;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.trading.api.dto.request.OrderCancelRequest;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.kafka.event.OrderCancelEvent;
import org.tradinggate.backend.trading.kafka.event.OrderEvent;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * [A-1] Trading API - Kafka 이벤트 발행
 * 역할:
 * - orders.in 토픽에 주문/취소 이벤트 발행
 * - ⚠️ key = symbol (같은 심볼은 같은 파티션으로) ⚠️
 * TODO:
 * [✅️] publishNewOrder(OrderCreateRequest, Long userId) 구현:
 *     1. OrderCreateRequest → OrderEvent 변환
 *        - commandType = "NEW"
 *        - source = "API"
 *        - receivedAt = 현재 시각 (ISO 8601)
 *     2. Kafka Send:
 *        - Topic: "orders.in"
 *        - Key: request.getSymbol()
 *        - Value: OrderEvent (JSON)
 *     3. 비동기 전송 + CompletableFuture 콜백:
 *        - 성공: 로그 기록
 *        - 실패: 로그 + 예외 처리 (재시도 3회)
 * [ ] publishCancelOrder(OrderCancelRequest, Long userId) 구현:
 *     1. OrderCancelRequest → OrderCancelEvent 변환
 *        - commandType = "CANCEL"
 *        - cancelTarget 설정
 *     2. Kafka Send (동일 방식)
 * [ ] 재시도 정책: 3회 재시도, 지수 백오프
 * [ ] 에러 처리:
 *     - KafkaException 발생 시 로그 + 알림
 * 참고: PDF 1-1 (orders.in 이벤트 구조)
 */

@Slf4j
@Component
@Profile("api")
@RequiredArgsConstructor
public class OrderEventProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private static final String TOPIC = "orders.in";

  /**신규 주문 이벤트 발행*/
  public void publishNewOrder(OrderCreateRequest request, Long userId) {
    OrderEvent event = OrderEvent.builder()
        .commandType("NEW")
        .userId(userId)
        .clientOrderId(request.getClientOrderId())
        .symbol(request.getSymbol())
        .side(request.getSide().name())
        .orderType(request.getOrderType().name())
        .timeInForce(request.getTimeInForce().name())
        .price(request.getPrice())
        .quantity(request.getQuantity())
        .source("API")
        .receivedAt(ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT))
        .build();

    kafkaTemplate.send(TOPIC, request.getSymbol(), event);
    log.info("Published NEW order: clientOrderId={}, symbol={}", request.getClientOrderId(), request.getSymbol());
  }

  /**주문 취소 이벤트 발행*/
  public void publishCancelOrder(OrderCancelRequest request, Long userId) {
    OrderCancelEvent event = OrderCancelEvent.builder()
        .commandType("CANCEL")
        .userId(userId)
        .clientOrderId(request.getClientOrderId())
        .symbol(request.getSymbol())
        .cancelTarget(OrderCancelEvent.CancelTarget.builder()
            .by("CLIENT_ORDER_ID")
            .value(request.getClientOrderId())
            .build())
        .source("API")
        .receivedAt(ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT))
        .build();

    kafkaTemplate.send(TOPIC, request.getSymbol(), event);
    log.info("Published CANCEL order: clientOrderId={}, symbol={}", request.getClientOrderId(), request.getSymbol());
  }
}
