package org.tradinggate.backend.trading.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [A-1] Trading API - 주문 Entity
 * 역할:
 * - Trading DB trading_order 테이블 매핑
 * - Projection Consumer가 orders.updated 이벤트를 받아 저장
 * 참고: PDF 1 (trading_order 테이블 구조)
 */
@Entity
@Table(
    name = "trading_order",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "client_order_id"}),
        @UniqueConstraint(columnNames = {"order_id"})
    }
)

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  private Long orderId;

  @Column(name = "client_order_id", nullable = false, length = 64)
  private String clientOrderId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 32)
  private String symbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_side", nullable = false, length = 10)
  private OrderSide side;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_type", nullable = false, length = 10)
  private OrderType orderType;

  @Enumerated(EnumType.STRING)
  @Column(name = "time_in_force", nullable = false, length = 10)
  private TimeInForce timeInForce;  // ✅ 추가

  @Column(precision = 18, scale = 8)
  private BigDecimal price;

  @Column(nullable = false, precision = 18, scale = 8)
  private BigDecimal quantity;

  @Column(name = "filled_quantity", precision = 18, scale = 8)
  @Builder.Default  // ✅ @Column 아래로 이동
  private BigDecimal filledQuantity = BigDecimal.ZERO;


  @Column(name = "remaining_quantity", nullable = false, precision = 18, scale = 8)
  private BigDecimal remainingQuantity;

  @Column(name = "avg_filled_price", precision = 18, scale = 8)
  private BigDecimal avgFilledPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status;

  @Column(name = "reject_reason", length = 128)
  private String rejectReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "last_event_seq")
  private Integer lastEventSeq;  // ✅ 추가

  @Column(name = "last_event_time")
  private LocalDateTime lastEventTime;  // ✅ 추가

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
    if (this.filledQuantity == null) {  // ✅ 추가
      this.filledQuantity = BigDecimal.ZERO;
    }
    if (this.remainingQuantity == null) {
      this.remainingQuantity = this.quantity;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
