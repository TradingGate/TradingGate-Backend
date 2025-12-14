package org.tradinggate.backend.trading.domain.entity;

/**
 * [A-1] Trading API - 주문 상태 Enum
 */
public enum OrderStatus {
  NEW,
  PARTIALLY_FILLED,
  FILLED,
  CANCELED,
  REJECTED,
  EXPIRED
}
