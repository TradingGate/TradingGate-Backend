package org.tradinggate.backend.risk.repository.balance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalanceId;
import java.util.List;
import java.util.Optional;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, AccountBalanceId> {

  // 특정 계정의 특정 자산 잔고 조회
  Optional<AccountBalance> findByAccountIdAndAsset(Long accountId, String asset);

  // 특정 계정의 모든 자산 잔고 조회
  List<AccountBalance> findAllByAccountId(Long accountId);

  // 모든 잔고 조회 (대사용)
  List<AccountBalance> findAll();
}
