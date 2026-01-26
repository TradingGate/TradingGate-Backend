package org.tradinggate.backend.risk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.risk.domain.entity.AccountBalance;

import java.util.Optional;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, Long> {

  Optional<AccountBalance> findByAccountIdAndAsset(Long accountId, String asset);
}
