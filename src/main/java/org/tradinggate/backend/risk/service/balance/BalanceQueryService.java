package org.tradinggate.backend.risk.service.balance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.api.dto.response.BalanceResponse;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.repository.balance.AccountBalanceRepository;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 잔고 조회 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceQueryService {

  private final AccountBalanceRepository balanceRepository;

  /**
   * 특정 계정의 모든 자산 잔고 조회
   */
  public List<BalanceResponse> getAccountBalance(Long accountId) {
    return balanceRepository.findAllByAccountId(accountId)
        .stream()
        .map(BalanceResponse::from)
        .collect(Collectors.toList());
  }

  /**
   * 특정 계정의 특정 자산 잔고 조회
   */
  public BalanceResponse getAssetBalance(Long accountId, String asset) {
    return balanceRepository.findByAccountIdAndAsset(accountId, asset)
        .map(BalanceResponse::from)
        .orElseThrow(() -> new IllegalArgumentException(
            String.format("Balance not found: accountId=%d, asset=%s", accountId, asset)
        ));
  }
}
