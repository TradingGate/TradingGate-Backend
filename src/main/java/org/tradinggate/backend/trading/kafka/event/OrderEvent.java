package org.tradinggate.backend.trading.kafka.event;
/**
 * [A-1] Trading API - 신규 주문 이벤트
 *
 * 역할:
 * - orders.in 토픽에 발행할 이벤트 구조
 * - Matching Worker(A-2)가 소비
 *
 * TODO:
 * [ ] PDF 스키마에 맞춰 필드 정의:
 *     - String commandType = "NEW"
 *     - Long userId
 *     - String clientOrderId
 *     - String symbol
 *     - String side ("BUY" / "SELL")
 *     - String orderType ("LIMIT" / "MARKET")
 *     - String timeInForce ("GTC" / "IOC" / "FOK") ✅
 *     - BigDecimal price
 *     - BigDecimal quantity
 *     - String source ("API" / "SYSTEM" / "RISK")
 *     - String receivedAt (ISO 8601: "2025-11-17T12:34:56.789Z")
 *
 * [ ] JSON 직렬화 어노테이션 (@JsonProperty)
 *
 * [ ] Getter/Setter 또는 @Data
 *
 * 참고: PDF 1-1 (orders.in 구조)
 */
public class OrderEvent {

  // TODO: 필드 정의

  // TODO: Getter/Setter

  // TODO: Builder 패턴 (선택)
}
