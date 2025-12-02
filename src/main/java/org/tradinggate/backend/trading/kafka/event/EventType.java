package org.tradinggate.backend.trading.kafka.event;

/**
 * [A-1] Trading API - 이벤트 타입 Enum
 *
 * 역할:
 * - 이벤트 타입 상수 정의
 *
 * TODO:
 * [ ] Enum 값 정의:
 *     - NEW (신규 주문)
 *     - CANCEL (주문 취소)
 *     - CREATED (주문 생성됨)
 *     - STATUS_CHANGED (상태 변경)
 *     - CANCELED (취소됨)
 *     - REJECTED (거절됨)
 *     - TRADE_MATCHED (체결됨)
 *     - EXPIRED (만료됨)
 *
 * 참고: PDF 1-2 (orders.updated eventType)
 */
public enum EventType {
  // TODO: Enum 값 정의
}
