// org/tradinggate/backend/trading/kafka/event/OrderEvent.java
package org.tradinggate.backend.trading.kafka.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.tradinggate.backend.trading.domain.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * orders.in 토픽 - 신규 주문 이벤트 (PDF 스키마 기준)
 *
 * 토픽: orders.in
 * 파티션 키: symbol
 * 목적지: Matching Engine (A-2)
 *
 * PDF 스키마:
 * - commandType: "NEW"
 * - userId, clientOrderId, symbol, side, orderType, timeInForce, price, quantity
 * - source: "API"
 * - receivedAt: 수신 시각
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateEvent {

  @JsonProperty("commandType")
  private EventType commandType;

  @JsonProperty("userId")
  private Long userId;

  @JsonProperty("clientOrderId")
  private String clientOrderId;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("side")
  private OrderSide side;

  @JsonProperty("orderType")
  private OrderType orderType;

  @JsonProperty("timeInForce")
  private TimeInForce timeInForce;

  @JsonProperty("price")
  private BigDecimal price;

  @JsonProperty("quantity")
  private BigDecimal quantity;

  @JsonProperty("source")
  private SourceType source;

  @JsonProperty("receivedAt")
  private LocalDateTime receivedAt;

  /**
   * Order Entity -> OrderEvent 변환 (PDF 스키마)
   */
  public static OrderCreateEvent from(Order order, SourceType sourceType) {
    return OrderCreateEvent.builder()
        .commandType(EventType.NEW)
        .userId(order.getUserId())
        .clientOrderId(order.getClientOrderId())
        .symbol(order.getSymbol())
        .side(order.getOrderSide())
        .orderType(order.getOrderType())
        .timeInForce(order.getTimeInForce())
        .price(order.getPrice())
        .quantity(order.getQuantity())
        .source(sourceType)
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
