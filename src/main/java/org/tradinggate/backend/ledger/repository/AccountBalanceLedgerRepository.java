package org.tradinggate.backend.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.ledger.domain.AccountBalanceLedger;

import java.time.LocalDate;
import java.util.List;

public interface AccountBalanceLedgerRepository extends JpaRepository<AccountBalanceLedger, Long> {

    List<AccountBalanceLedger> findByBusinessDateAndAccountId(LocalDate businessDate, Long accountId);

    List<AccountBalanceLedger> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<AccountBalanceLedger> findByRefTypeAndRefId(String refType, Long refId);
}
