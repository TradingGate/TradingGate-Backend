package org.tradinggate.backend.risk.domain.entity.anomaly;

/**
 * 이상 패턴 타입
 *
 * MVP 구현:
 * - ORDER_FLOOD: 주문 폭주 (짧은 시간에 많은 주문)
 *
 * 확장 가능:
 * - CANCEL_REPEAT: 취소 반복
 * - PRICE_MANIPULATION: 가격 조작 의심
 * - LARGE_ORDER: 비정상적 대량 주문
 */
public enum PatternType {
  ORDER_FLOOD,
  CANCEL_REPEAT,
  PRICE_MANIPULATION,
  LARGE_ORDER
}
