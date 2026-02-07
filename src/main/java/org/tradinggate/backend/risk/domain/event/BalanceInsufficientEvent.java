package org.tradinggate.backend.risk.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 잔고 부족 이벤트 (내부 도메인 이벤트)
 *
 * 역할:
 * - RiskCheckService → RiskStateService로 전달
 * - Spring ApplicationEventPublisher 사용
 *
 * 흐름:
 * 1. RiskCheckService에서 음수 잔고 발견
 * 2. BalanceInsufficientEvent 발행
 * 3. RiskStateService가 @EventListener로 수신
 * 4. 계정 블락 처리
 */
@Getter
@AllArgsConstructor
@Builder
public class BalanceInsufficientEvent {

  private Long accountId;
  private String asset;
  private BigDecimal currentBalance;
  private String reason;
  private LocalDateTime occurredAt;

  /**
   * 팩토리 메서드
   */
  public static BalanceInsufficientEvent of(Long accountId, String asset,
                                            BigDecimal balance, String reason) {
    return BalanceInsufficientEvent.builder()
        .accountId(accountId)
        .asset(asset)
        .currentBalance(balance)
        .reason(reason)
        .occurredAt(LocalDateTime.now())
        .build();
  }
}
