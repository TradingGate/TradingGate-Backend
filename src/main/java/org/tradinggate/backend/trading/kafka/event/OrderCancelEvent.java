// org/tradinggate/backend/trading/kafka/event/OrderCancelEvent.java
package org.tradinggate.backend.trading.kafka.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.tradinggate.backend.trading.domain.entity.Order;

import java.time.LocalDateTime;

/**
 * orders.in 토픽 - 주문 취소 이벤트 (PDF 스키마 기준)
 *
 * 토픽: orders.in
 * 파티션 키: symbol
 * 목적지: Matching Engine (A-2)
 *
 * PDF 스키마:
 * - commandType: "CANCEL"
 * - userId, clientOrderId, symbol
 * - cancelTarget: { by: "CLIENT_ORDER_ID" or "ORDER_ID", value: "..." }
 * - source: "API"
 * - receivedAt: 수신 시각
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelEvent {

  @JsonProperty("commandType")
  private String commandType; // "CANCEL"

  @JsonProperty("userId")
  private Long userId;

  @JsonProperty("clientOrderId")
  private String clientOrderId;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("cancelTarget")
  private CancelTarget cancelTarget;

  @JsonProperty("source")
  private String source; // "API"

  @JsonProperty("receivedAt")
  private LocalDateTime receivedAt;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CancelTarget {
    @JsonProperty("by")
    private String by; // "CLIENT_ORDER_ID" or "ORDER_ID"

    @JsonProperty("value")
    private String value; // clientOrderId 또는 orderId 값
  }

  /**
   * Order Entity -> OrderCancelEvent 변환
   * - clientOrderId 기준으로 취소
   */
  public static OrderCancelEvent from(Order order) {
    return OrderCancelEvent.builder()
        .commandType("CANCEL")
        .userId(order.getUserId())
        .clientOrderId(order.getClientOrderId())
        .symbol(order.getSymbol())
        .cancelTarget(CancelTarget.builder()
            .by("CLIENT_ORDER_ID")
            .value(order.getClientOrderId())
            .build())
        .source("API")
        .receivedAt(LocalDateTime.now())
        .build();
  }

  /**
   * orderId 기준으로 취소하는 경우 (추가 팩토리 메서드)
   */
  public static OrderCancelEvent fromOrderId(Order order) {
    return OrderCancelEvent.builder()
        .commandType("CANCEL")
        .userId(order.getUserId())
        .clientOrderId(order.getClientOrderId())
        .symbol(order.getSymbol())
        .cancelTarget(CancelTarget.builder()
            .by("ORDER_ID") // ✅ orderId로 취소
            .value(order.getOrderId() != null ? order.getOrderId().toString() : null)
            .build())
        .source("API")
        .receivedAt(LocalDateTime.now())
        .build();
  }

  /**
   * Kafka 파티션 키 추출 (심볼 기준 파티셔닝)
   */
  public String getPartitionKey() {
    return this.symbol;
  }
}
