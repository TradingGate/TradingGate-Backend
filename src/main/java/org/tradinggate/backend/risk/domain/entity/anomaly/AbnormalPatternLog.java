package org.tradinggate.backend.risk.domain.entity.anomaly;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 이상 거래 패턴 로그
 *
 * 역할:
 * - 이상 패턴 감지 시 자동 기록
 * - 로그 기반 모니터링 및 분석
 * - 일정 임계값 초과 시 계정 블락
 */
@Entity
@Table(name = "abnormal_pattern_log", indexes = {
    @Index(name = "idx_account_pattern", columnList = "accountId,patternType"),
    @Index(name = "idx_detected_at", columnList = "detectedAt"),
    @Index(name = "idx_action_taken", columnList = "actionTaken")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AbnormalPatternLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * 계정 ID
   */
  @Column(nullable = false)
  private Long accountId;

  /**
   * 심볼 (선택, 심볼별 감지 시)
   */
  @Column(length = 20)
  private String symbol;

  /**
   * 패턴 타입
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PatternType patternType;

  /**
   * 상세 설명
   * 예: "Order flood: 150 orders in 1 minute"
   */
  @Column(length = 500)
  private String description;

  /**
   * 조치 여부
   */
  @Column(nullable = false)
  @Builder.Default
  private Boolean actionTaken = false;

  /**
   * 조치 내용
   * 예: "Account blocked", "Warning sent"
   */
  @Column(length = 200)
  private String action;

  /**
   * 감지 시각
   */
  @Column(nullable = false)
  private LocalDateTime detectedAt;

  @PrePersist
  protected void onCreate() {
    if (this.detectedAt == null) {
      this.detectedAt = LocalDateTime.now();
    }
  }

  /**
   * 조치 완료 표시
   */
  public void markActionTaken(String action) {
    this.actionTaken = true;
    this.action = action;
  }
}
