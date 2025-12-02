package org.tradinggate.backend.trading.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "orders",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_client_order", columnNames = {"user_id", "client_order_id"})
    },
    indexes = {
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_user_created", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_user_symbol_status", columnList = "user_id, symbol, status")
    }
)

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", unique = true, nullable = false)
  private Long orderId;

  @Column(name = "client_order_id", length = 64, nullable = false)
  private String clientOrderId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "symbol", length = 32, nullable = false)
  private String symbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "side", nullable = false, length = 4)
  private OrderSide side;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_type", nullable = false, length = 10)
  private OrderType orderType;

  @Enumerated(EnumType.STRING)
  @Column(name = "time_in_force", nullable = false, length = 3)
  private TimeInForce timeInForce;

  @Column(name = "price", precision = 18, scale = 8)
  private BigDecimal price;

  @Column(name = "quantity", precision = 18, scale = 8, nullable = false)
  private BigDecimal quantity;

  @Column(name = "filled_quantity", precision = 18, scale = 8, nullable = false)
  @Builder.Default
  private BigDecimal filledQuantity = BigDecimal.ZERO;

  @Column(name = "remaining_quantity", precision = 18, scale = 8, nullable = false)
  private BigDecimal remainingQuantity;

  @Column(name = "avg_filled_price", precision = 18, scale = 8)
  private BigDecimal avgFilledPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrderStatus status;

  @Column(name = "reject_reason", length = 128)
  private String rejectReason;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "last_event_seq")
  private Integer lastEventSeq;

  @Column(name = "last_event_time")
  private LocalDateTime lastEventTime;
}
