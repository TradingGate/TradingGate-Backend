package org.tradinggate.backend.risk.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_risk_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRiskProfile {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RiskStatus status; // NORMAL, WARNING, BLOCKED

  // B-2 설정: 최대 보유 가능 수량 / 금액
  @Column(name = "max_position_value")
  private BigDecimal maxPositionValue; // 예: $100,000

  // B-3 설정: 마진콜 발동 비율 (예: 1.1 -> 증거금이 필요량의 110% 미만이면 경고)
  @Column(name = "margin_call_threshold")
  private BigDecimal marginCallThreshold;

  // B-3 설정: 강제청산 발동 비율 (예: 0.5 -> 증거금이 50% 남으면 청산)
  @Column(name = "liquidation_threshold")
  private BigDecimal liquidationThreshold;

  private LocalDateTime updatedAt;

  // 생성자 (Factory Method)
  public static UserRiskProfile createDefault(Long userId) {
    UserRiskProfile profile = new UserRiskProfile();
    profile.userId = userId;
    profile.status = RiskStatus.NORMAL;
    profile.maxPositionValue = new BigDecimal("100000"); // 기본 10만불
    profile.marginCallThreshold = new BigDecimal("0.10"); // 증거금 잔고 10% 남으면 경고
    profile.liquidationThreshold = new BigDecimal("0.05"); // 증거금 잔고 5% 남으면 청산
    profile.updatedAt = LocalDateTime.now();
    return profile;
  }

  public void updateStatus(RiskStatus newStatus) {
    this.status = newStatus;
    this.updatedAt = LocalDateTime.now();
  }
}