package org.tradinggate.backend.trading.kafka.event;

/**
 * [A-1] Trading API - 이벤트 타입 Enum
 * 역할:
 * - 이벤트 타입 상수 정의
 */
public enum EventType {
  NEW, CANCEL, CREATED, STATUS_CHANGED, CANCELED, REJECTED, TRADE_MATCHED, EXPIRED
}
