package org.tradinggate.backend.risk.repository.balance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, Long> {

  Optional<AccountBalance> findByAccountIdAndAsset(Long accountId, String asset);
}
