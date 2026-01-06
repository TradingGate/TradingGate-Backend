package org.tradinggate.backend.risk.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@DynamicUpdate
@Table(name = "position", uniqueConstraints = {
    @UniqueConstraint(name = "uk_position_acc_sym", columnNames = {"account_id", "symbol_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "symbol_id", nullable = false)
  private Long symbolId;

  @Column(nullable = false, precision = 36, scale = 18)
  private BigDecimal quantity;

  @Column(name = "avg_price", nullable = false, precision = 18, scale = 8)
  private BigDecimal avgPrice;

  @Column(name = "realized_pnl", nullable = false, precision = 18, scale = 8)
  private BigDecimal realizedPnl;

  @Column(name = "unrealized_pnl", nullable = false, precision = 18, scale = 8)
  private BigDecimal unrealizedPnl;

  @Column(name = "last_valuated_price", precision = 18, scale = 8)
  private BigDecimal lastValuatedPrice;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  public void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
