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
    BigDecimal oldQuantity = this.quantity;

    // tradeQuantity는 부호가 반대이므로 더하면 수량이 줄어듦 (예: 보유 10 + 매도 -5 = 5)
    this.quantity = this.quantity.add(tradeQuantity);
    this.realizedPnl = this.realizedPnl.add(pnl);

    // 🔥 케이스 1: 완전 청산 (수량 0)
    if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
      this.avgPrice = BigDecimal.ZERO;
      this.unrealizedPnl = BigDecimal.ZERO;
    }
    // 🔥 케이스 2: 방향 전환 (롱→숏 또는 숏→롱)
    // 기존 수량과 새 수량의 부호가 다르면 반대 포지션 진입
    // 이 경우 평단가는 PositionService에서 새 진입가로 초기화해야 함
    // (여기서는 수량만 업데이트하고, 평단가 초기화는 외부에서 resetAvgPrice() 호출)
  }

  // 3. 평가 손익 갱신 (Mark-to-Market)
  public void updateValuation(BigDecimal currentPrice, BigDecimal unrealizedPnl) {
    this.lastValuatedPrice = currentPrice;
    this.unrealizedPnl = unrealizedPnl;
  }

  // 🔥 4. 반대 포지션 진입 시 평단가 초기화 (추가)
  /**
   * 전체 청산 후 반대 방향 포지션 진입 시 평단가를 새 진입가로 초기화
   * @param newAvgPrice 새 진입가
   */
  public void resetAvgPrice(BigDecimal newAvgPrice) {
    this.avgPrice = newAvgPrice;
    this.unrealizedPnl = BigDecimal.ZERO; // 반대 포지션 진입 시 미실현손익도 초기화
  }

  @PrePersist
  @PreUpdate
  public void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
