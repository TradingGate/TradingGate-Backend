package org.tradinggate.backend.trading.kafka.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * [A-1] Trading API - 주문 취소 이벤트
 * 역할:
 * - orders.in 토픽에 발행할 취소 이벤트
 * TODO:
 * [✅️] PDF 스키마에 맞춰 필드 정의:
 *     - String commandType = "CANCEL"
 *     - Long userId
 *     - String clientOrderId
 *     - String symbol
 *     - CancelTarget cancelTarget
 *     - String source = "API"
 *     - String receivedAt
 * [✅️] CancelTarget 내부 클래스 추가:
 *     public static class CancelTarget {
 *         private String by;    // "CLIENT_ORDER_ID" or "ORDER_ID"
 *         private String value; // clientOrderId or orderId 값
 *     }
 * [ ] JSON 직렬화 어노테이션
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCancelEvent {

  @JsonProperty("commandType")
  private String commandType;  // "CANCEL"

  @JsonProperty("userId")
  private Long userId;

  @JsonProperty("clientOrderId")
  private String clientOrderId;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("cancelTarget")
  private CancelTarget cancelTarget;

  @JsonProperty("source")
  private String source;  // "API"

  @JsonProperty("receivedAt")
  private String receivedAt;

  /**
   * 취소 대상 지정
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class CancelTarget {
    @JsonProperty("by")
    private String by;  // "CLIENT_ORDER_ID" or "ORDER_ID"

    @JsonProperty("value")
    private String value;  // clientOrderId 또는 orderId 값
  }
}