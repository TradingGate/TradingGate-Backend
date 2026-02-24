package org.tradinggate.backend.risk.service.balance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.repository.balance.AccountBalanceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@Profile("risk")
@RequiredArgsConstructor
public class BalanceService {

  private final AccountBalanceRepository balanceRepository;

  /**
   * 잔고 업데이트 (증감)
   * - 없으면 생성, 있으면 업데이트
   */
  @Transactional
  public void updateBalance(Long accountId, String asset, BigDecimal amount) {
    String normalizedAsset = normalizeAsset(asset);
    AccountBalance balance = balanceRepository
        .findByAccountIdAndAsset(accountId, normalizedAsset)
        .orElseGet(() -> createNewBalance(accountId, normalizedAsset));
    balance.addAvailable(amount);
    balanceRepository.save(balance);
    log.info("잔고 업데이트: accountId={}, asset={}, amount={}, new balance={}",
        accountId, normalizedAsset, amount, balance.getAvailable());
  }

  /**
   * 여러 자산 잔고 동시 업데이트
   */
  @Transactional
  public void updateBalances(Long accountId, java.util.Map<String, BigDecimal> changes) {
    java.util.Map<String, BigDecimal> normalized = new java.util.HashMap<>();
    for (java.util.Map.Entry<String, BigDecimal> entry : changes.entrySet()) {
      normalized.merge(normalizeAsset(entry.getKey()), entry.getValue(), BigDecimal::add);
    }
    for (java.util.Map.Entry<String, BigDecimal> entry : normalized.entrySet()) {
      updateBalance(accountId, entry.getKey(), entry.getValue());
    }
  }

  /**
   * 새 잔고 생성
   */
  private AccountBalance createNewBalance(Long accountId, String asset) {
    return AccountBalance.builder()
        .accountId(accountId)
        .asset(asset)
        .available(BigDecimal.ZERO)
        .locked(BigDecimal.ZERO)
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private String normalizeAsset(String asset) {
    return asset == null ? null : asset.toUpperCase();
  }
}
