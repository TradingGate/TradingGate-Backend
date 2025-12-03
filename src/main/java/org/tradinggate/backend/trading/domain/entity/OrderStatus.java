package org.tradinggate.backend.trading.domain.entity;
/**
 * [A-1] Trading API - 주문 상태 Enum
 *
 * TODO:
 * [v] Enum 값 확인:
 *     - NEW (신규)
 *     - PARTIALLY_FILLED (부분 체결)
 *     - FILLED (전량 체결)
 *     - CANCELED (취소됨)
 *     - REJECTED (거절됨)
 *     - EXPIRED (만료됨)
 *
 */
public enum OrderStatus {
  NEW,
  PARTIALLY_FILLED,
  FILLED,
  CANCELED,
  REJECTED,
  EXPIRED
}
