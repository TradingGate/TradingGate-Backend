// org/tradinggate/backend/trading/api/dto/response/OrderResponse.java
package org.tradinggate.backend.trading.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderStatus;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [A-1] Trading API - 주문 응답 DTO
 *
 * 역할:
 * - API → 클라이언트 주문 정보 응답
 * - Entity의 필요한 필드만 노출 (민감 정보 제외)
 *
 * 변환:
 * - Entity → DTO: static from(Order order)
 *
 * 참고: PDF 1 (trading_order 테이블 구조)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

  /**
   * ✅ DB의 PK (Auto Increment)
   */
  @JsonProperty("id")
  private Long id;

  /**
   * ✅ Matching Worker가 발행한 주문 ID (null일 수 있음)
   */
  @JsonProperty("order_id")
  private Long orderId;

  /**
   * ✅ 클라이언트가 제공한 주문 ID (주 식별자)
   */
  @JsonProperty("client_order_id")
  private String clientOrderId;

  @JsonProperty("user_id")
  private Long userId;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("order_side")
  private OrderSide orderSide;

  @JsonProperty("order_type")
  private OrderType orderType;

  @JsonProperty("time_in_force")
  private TimeInForce timeInForce;

  @JsonProperty("price")
  private BigDecimal price;

  @JsonProperty("quantity")
  private BigDecimal quantity;

  @JsonProperty("filled_quantity")
  private BigDecimal filledQuantity;

  @JsonProperty("remaining_quantity")
  private BigDecimal remainingQuantity;

  @JsonProperty("avg_filled_price")
  private BigDecimal avgFilledPrice;

  @JsonProperty("status")
  private OrderStatus status;

  @JsonProperty("reject_reason")
  private String rejectReason;

  @JsonProperty("last_event_seq")
  private Long lastEventSeq;

  @JsonProperty("last_event_time")
  private LocalDateTime lastEventTime;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  @JsonProperty("updated_at")
  private LocalDateTime updatedAt;

  /**
   * Entity → DTO 변환 (정적 팩토리 메서드)
   * 사용 예시:
   * OrderResponse response = OrderResponse.from(order);
   */
  public static OrderResponse from(Order order) {
    return OrderResponse.builder()
        .id(order.getId())
        .orderId(order.getOrderId())
        .clientOrderId(order.getClientOrderId())
        .userId(order.getUserId())
        .symbol(order.getSymbol())
        .orderSide(order.getOrderSide())
        .orderType(order.getOrderType())
        .timeInForce(order.getTimeInForce())
        .price(order.getPrice())
        .quantity(order.getQuantity())
        .filledQuantity(order.getFilledQuantity())
        .remainingQuantity(order.getRemainingQuantity())
        .avgFilledPrice(order.getAvgFilledPrice())
        .status(order.getStatus())
        .rejectReason(order.getRejectReason())
        .lastEventSeq(order.getLastEventSeq())
        .lastEventTime(order.getLastEventTime())
        .createdAt(order.getCreatedAt())
        .updatedAt(order.getUpdatedAt())
        .build();
  }
}
