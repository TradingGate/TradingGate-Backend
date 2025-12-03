package org.tradinggate.backend.trading.kafka.event;

/**
 * [A-1] Trading API - 주문 취소 이벤트
 *
 * 역할:
 * - orders.in 토픽에 발행할 취소 이벤트
 *
 * TODO:
 * [ ] PDF 스키마에 맞춰 필드 정의:
 *     - String commandType = "CANCEL"
 *     - Long userId
 *     - String clientOrderId
 *     - String symbol
 *     - CancelTarget cancelTarget
 *     - String source = "API"
 *     - String receivedAt
 *
 * [ ] CancelTarget 내부 클래스 추가:
 *     public static class CancelTarget {
 *         private String by;    // "CLIENT_ORDER_ID" or "ORDER_ID"
 *         private String value; // clientOrderId or orderId 값
 *     }
 *
 * [ ] JSON 직렬화 어노테이션
 *
 * 참고: PDF 3-2 (CANCEL Command 구조)
 */
public class OrderCancelEvent {

  // TODO: 필드 정의

  // TODO: CancelTarget 내부 클래스

  // TODO: Getter/Setter
}
