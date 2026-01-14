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

  public Position(Long accountId, Long symbolId) {
    this.accountId = accountId;
    this.symbolId = symbolId;
    this.quantity = BigDecimal.ZERO;
    this.avgPrice = BigDecimal.ZERO;
    this.realizedPnl = BigDecimal.ZERO;
    this.unrealizedPnl = BigDecimal.ZERO;
    this.updatedAt = LocalDateTime.now();
  }

  public static Position createDefault(Long accountId, Long symbolId) {
    return new Position(accountId, symbolId);
  }

  // 1. 포지션 증가 (진입): 평단가 갱신
  public void increasePosition(BigDecimal newTotalQuantity, BigDecimal newAvgPrice) {
    this.quantity = newTotalQuantity;
    this.avgPrice = newAvgPrice;
  }

  // 2. 포지션 감소 (청산): 수량 감소 & 실현손익 누적
  public void decreasePosition(BigDecimal tradeQuantity, BigDecimal pnl) {
    // tradeQuantity는 부호가 반대이므로 더하면 수량이 줄어듦 (예: 보유 10 + 매도 -5 = 5)
    this.quantity = this.quantity.add(tradeQuantity);
    this.realizedPnl = this.realizedPnl.add(pnl);

    // 완전 청산(수량 0) 시 평단가 및 미실현손익 초기화
    if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
      this.avgPrice = BigDecimal.ZERO;
      this.unrealizedPnl = BigDecimal.ZERO;
    }
  }

  // 3. 평가 손익 갱신 (Mark-to-Market)
  public void updateValuation(BigDecimal currentPrice, BigDecimal unrealizedPnl) {
    this.lastValuatedPrice = currentPrice;
    this.unrealizedPnl = unrealizedPnl;
  }

  @PrePersist
  @PreUpdate
  public void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
