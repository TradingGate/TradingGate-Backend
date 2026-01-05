package org.tradinggate.backend.trading.kafka.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.SourceType;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelEvent {

  @JsonProperty("commandType")
  private EventType commandType;

  @JsonProperty("userId")
  private Long userId;

  @JsonProperty("clientOrderId")
  private String clientOrderId;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("cancelTarget")
  private CancelTarget cancelTarget;

  @JsonProperty("source")
  private SourceType source;

  @JsonProperty("receivedAt")
  private LocalDateTime receivedAt;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CancelTarget {
    @JsonProperty("by")
    private CancelBy by;

    @JsonProperty("value")
    private String value;
  }

  public enum CancelBy {
    CLIENT_ORDER_ID,
    ORDER_ID
  }

  /**
   * Order Entity -> OrderCancelEvent 변환
   */
  public static OrderCancelEvent from(Order order, SourceType sourceType) {
    return OrderCancelEvent.builder()
        .commandType(EventType.CANCEL)
        .userId(order.getUserId())
        .clientOrderId(order.getClientOrderId())
        .symbol(order.getSymbol())
        .cancelTarget(CancelTarget.builder()
            .by(CancelBy.CLIENT_ORDER_ID)
            .value(order.getClientOrderId())
            .build())
        .source(sourceType)
        .receivedAt(LocalDateTime.now())
        .build();
  }

  /**
   * orderId 기준 취소
   */
  public static OrderCancelEvent fromOrderId(Order order, SourceType sourceType) {
    return OrderCancelEvent.builder()
        .commandType(EventType.CANCEL)
        .userId(order.getUserId())
        .clientOrderId(order.getClientOrderId())
        .symbol(order.getSymbol())
        .cancelTarget(CancelTarget.builder()
            .by(CancelBy.ORDER_ID)
            .value(order.getOrderId() != null ? order.getOrderId().toString() : null)
            .build())
        .source(sourceType)
        .receivedAt(LocalDateTime.now())
        .build();
  }

  public String getPartitionKey() {
    return this.symbol;
  }
}
