package org.tradinggate.backend.risk.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(
    name = "account_balance",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_account_asset",
        columnNames = {"account_id", "asset"}
    ),
    indexes = {
        @Index(name = "idx_account_id", columnList = "account_id"),
        @Index(name = "idx_asset", columnList = "asset")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBalance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(nullable = false, length = 32)
  private String asset; // BTC, USDT 등

  @Column(name = "total_balance", nullable = false, precision = 36, scale = 18)
  private BigDecimal totalBalance = BigDecimal.ZERO;

  @Column(name = "available_balance", nullable = false, precision = 36, scale = 18)
  private BigDecimal availableBalance = BigDecimal.ZERO;

  @Column(name = "locked_balance", nullable = false, precision = 36, scale = 18)
  private BigDecimal lockedBalance = BigDecimal.ZERO;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public static AccountBalance create(Long accountId, String asset) {
    AccountBalance balance = new AccountBalance();
    balance.accountId = accountId;
    balance.asset = asset;
    balance.totalBalance = BigDecimal.ZERO;
    balance.availableBalance = BigDecimal.ZERO;
    balance.lockedBalance = BigDecimal.ZERO;
    balance.updatedAt = LocalDateTime.now();
    return balance;
  }

  public void addTotalBalance(BigDecimal amount) {
    this.totalBalance = this.totalBalance.add(amount);
    this.updatedAt = LocalDateTime.now();
  }

  public void subtractTotalBalance(BigDecimal amount) {
    this.totalBalance = this.totalBalance.subtract(amount);
    this.updatedAt = LocalDateTime.now();
  }

  public void addAvailableBalance(BigDecimal amount) {
    this.availableBalance = this.availableBalance.add(amount);
    this.updatedAt = LocalDateTime.now();
  }

  public void subtractAvailableBalance(BigDecimal amount) {
    this.availableBalance = this.availableBalance.subtract(amount);
    this.updatedAt = LocalDateTime.now();
  }

  public void addLockedBalance(BigDecimal amount) {
    this.lockedBalance = this.lockedBalance.add(amount);
    this.updatedAt = LocalDateTime.now();
  }

  public void subtractLockedBalance(BigDecimal amount) {
    this.lockedBalance = this.lockedBalance.subtract(amount);
    this.updatedAt = LocalDateTime.now();
  }

  @PrePersist
  @PreUpdate
  public void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
