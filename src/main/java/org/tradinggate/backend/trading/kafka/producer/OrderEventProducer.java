package org.tradinggate.backend.trading.kafka.product;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [A-1] Trading API - Kafka 이벤트 발행
 *
 * 역할:
 * - orders.in 토픽에 주문/취소 이벤트 발행
 * - ⚠️ key = symbol (같은 심볼은 같은 파티션으로) ⚠️
 *
 * TODO:
 * [ ] publishNewOrder(OrderCreateRequest, Long userId) 구현:
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
 *
 * [ ] publishCancelOrder(OrderCancelRequest, Long userId) 구현:
 *     1. OrderCancelRequest → OrderCancelEvent 변환
 *        - commandType = "CANCEL"
 *        - cancelTarget 설정
 *     2. Kafka Send (동일 방식)
 *
 * [ ] 재시도 정책: 3회 재시도, 지수 백오프
 *
 * [ ] 에러 처리:
 *     - KafkaException 발생 시 로그 + 알림
 *
 * 참고: PDF 1-1 (orders.in 이벤트 구조)
 */
@Component
@Profile("api")
public class OrderEventProducer {

  // TODO: KafkaTemplate<String, Object> 주입

  // TODO: publishNewOrder() 구현

  // TODO: publishCancelOrder() 구현

  // TODO: 비동기 콜백 처리
}
