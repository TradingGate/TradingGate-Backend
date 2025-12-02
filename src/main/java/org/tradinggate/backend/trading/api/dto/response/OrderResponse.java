package org.tradinggate.backend.trading.api.dto.response;
/**
 * [A-1] Trading API - 주문 응답 DTO
 *
 * 역할:
 * - API → 클라이언트 주문 정보 응답
 *
 * TODO:
 * [ ] Order Entity → OrderResponse 변환 메서드 추가
 *     - static OrderResponse from(Order order)
 *
 * [ ] 필요한 필드만 노출 (민감 정보 제외):
 *     - orderId, clientOrderId, userId
 *     - symbol, side, orderType, timeInForce
 *     - price, quantity, filledQuantity, remainingQuantity
 *     - status, rejectReason
 *     - createdAt, updatedAt
 *     - lastEventSeq, lastEventTime ✅
 *
 * 참고: PDF 1 (trading_order 테이블 구조)
 */
public class OrderResponse {

  // TODO: 필드 정의

  // TODO: from(Order) 변환 메서드

  // TODO: Getter (Jackson 자동 직렬화)
}
