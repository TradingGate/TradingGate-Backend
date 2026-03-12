package org.tradinggate.backend.risk.domain.entity.risk;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 리스크 상태 엔티티
 *
 * 역할:
 * - 계정별 리스크 상태 관리
 * - NORMAL: 정상 거래 가능
 * - BLOCKED: 거래 차단 (잔고 부족 등)
 *
 * 특징:
 * - accountId = PK (계정당 하나의 상태)
 * - 상태 변경 시 A 모듈에 Kafka로 알림
 */
@Entity
@Table(name = "risk_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RiskState {

  @Id
  private Long accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private RiskStatus status = RiskStatus.NORMAL;

  @Column(length = 500)
  private String blockReason;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }


  public void block(String reason) {
    this.status = RiskStatus.BLOCKED;
    this.blockReason = reason;
  }

  public void unblock() {
    this.status = RiskStatus.NORMAL;
    this.blockReason = null;
  }

  public boolean isBlocked() {
    return this.status == RiskStatus.BLOCKED;
  }

  public boolean isNormal() {
    return this.status == RiskStatus.NORMAL;
  }
}
