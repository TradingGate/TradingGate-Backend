package org.tradinggate.backend.trading.api.dto.request;
/**
 * [A-1] Trading API - 주문 취소 요청 DTO
 *
 * 역할:
 * - 클라이언트 → API 취소 요청
 *
 * TODO:
 * [ ] 필드 구조 확인:
 *     - String clientOrderId (clientOrderId로 취소)
 *     - String symbol (필수)
 *     - Long orderId (orderId로 취소, 선택)
 *
 * [ ] Validation:
 *     - clientOrderId 또는 orderId 둘 중 하나 필수
 *     - @NotBlank(message = "symbol is required")
 *
 * [ ] CancelTarget 구조 추가 (선택):
 *     - cancelTarget.by ("CLIENT_ORDER_ID" or "ORDER_ID")
 *     - cancelTarget.value
 *
 * 참고: PDF 3-2 (취소 Command 구조)
 */
public class OrderCancelRequest {

  // TODO: 필드 정의

  // TODO: Validation 로직 추가

  // TODO: CancelTarget 내부 클래스 (선택)
}
