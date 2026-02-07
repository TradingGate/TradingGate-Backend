package org.tradinggate.backend.risk.service.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.domain.event.BalanceInsufficientEvent;
import org.tradinggate.backend.risk.repository.balance.AccountBalanceRepository;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskCheckService {

  private final AccountBalanceRepository balanceRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 계정의 모든 자산 잔고 음수 체크
   */
  public void checkNegativeBalance(Long accountId) {
    List<AccountBalance> balances = balanceRepository.findAllByAccountId(accountId);

    for (AccountBalance balance : balances) {
      if (balance.isNegative()) {
        handleNegativeBalance(accountId, balance);
      }
    }
  }

  /**
   * 특정 자산 잔고 음수 체크
   */
  public void checkNegativeBalance(Long accountId, String asset) {
    balanceRepository.findByAccountIdAndAsset(accountId, asset)
        .ifPresent(balance -> {
          if (balance.isNegative()) {
            handleNegativeBalance(accountId, balance);
          }
        });
  }

  /**
   * 음수 잔고 처리
   * - 에러 로그
   * - BalanceInsufficientEvent 발행
   */
  private void handleNegativeBalance(Long accountId, AccountBalance balance) {
    log.error("❌ NEGATIVE BALANCE DETECTED: accountId={}, asset={}, balance={}",
        accountId, balance.getAsset(), balance.getAvailable());

    // 내부 도메인 이벤트 발행
    BalanceInsufficientEvent event = BalanceInsufficientEvent.of(
        accountId,
        balance.getAsset(),
        balance.getAvailable(),
        String.format("Negative balance: %s = %s",
            balance.getAsset(), balance.getAvailable()));

    eventPublisher.publishEvent(event);

    log.warn("📢 BalanceInsufficientEvent published: accountId={}, asset={}",
        accountId, balance.getAsset());
  }
}
