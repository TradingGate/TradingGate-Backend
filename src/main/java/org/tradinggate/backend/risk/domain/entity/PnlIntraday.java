package org.tradinggate.backend.risk.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "pnl_intraday", indexes = {
    @Index(name = "idx_pnl_date_acc", columnList = "business_date, account_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PnlIntraday {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "symbol_id", nullable = false)
  private Long symbolId;

  @Column(name = "realized_pnl", nullable = false, precision = 18, scale = 8)
  private BigDecimal realizedPnl;

  @Column(name = "unrealized_pnl", nullable = false, precision = 18, scale = 8)
  private BigDecimal unrealizedPnl;

  @Column(name = "fee", nullable = false, precision = 18, scale = 8)
  private BigDecimal fee;

  @Column(name = "snapshot_time", nullable = false)
  private LocalDateTime snapshotTime;

  @PrePersist
  public void onPrePersist() {
    this.snapshotTime = LocalDateTime.now();
  }
}
