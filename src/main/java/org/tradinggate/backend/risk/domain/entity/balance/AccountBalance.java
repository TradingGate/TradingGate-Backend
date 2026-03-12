package org.tradinggate.backend.risk.domain.entity.balance;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_balance")
@IdClass(AccountBalanceId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AccountBalance {

  @Id
  @Column(nullable = false)
  private Long accountId;

  @Id
  @Column(nullable = false, length = 10)
  private String asset;

  @Column(nullable = false, precision = 20, scale = 8)
  @Builder.Default
  private BigDecimal available = BigDecimal.ZERO;

  @Column(nullable = false, precision = 20, scale = 8)
  @Builder.Default
  private BigDecimal locked = BigDecimal.ZERO;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }

  public void addAvailable(BigDecimal amount) {
    this.available = this.available.add(amount);
  }

  public boolean isNegative() {
    return this.available.compareTo(BigDecimal.ZERO) < 0;
  }

  public BigDecimal getTotal() {
    return this.available.add(this.locked);
  }
}
