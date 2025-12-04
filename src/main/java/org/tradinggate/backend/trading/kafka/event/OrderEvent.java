package org.tradinggate.backend.trading.kafka.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * [A-1] Trading API - 신규 주문 이벤트
 * 역할:
 * - orders.in 토픽에 발행할 이벤트 구조
 * - Matching Worker(A-2)가 소비
 * TODO:
 * [✅️] PDF 스키마에 맞춰 필드 정의:
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
 * [ ] JSON 직렬화 어노테이션 (@JsonProperty)
 * [✅️] Getter/Setter 또는 @Data
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {

  @JsonProperty("commandType")
  private String commandType;  // "NEW"

  @JsonProperty("userId")
  private Long userId;

  @JsonProperty("clientOrderId")
  private String clientOrderId;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("side")
  private String side;  // "BUY" or "SELL"

  @JsonProperty("orderType")
  private String orderType;  // "LIMIT" or "MARKET"

  @JsonProperty("timeInForce")
  private String timeInForce;  // "GTC", "IOC", "FOK"

  @JsonProperty("price")
  private BigDecimal price;

  @JsonProperty("quantity")
  private BigDecimal quantity;

  @JsonProperty("source")
  private String source;  // "API", "SYSTEM", "RISK"

  @JsonProperty("receivedAt")
  private String receivedAt;  // ISO 8601 타임스탬프
}