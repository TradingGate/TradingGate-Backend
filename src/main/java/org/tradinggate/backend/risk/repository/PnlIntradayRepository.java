package org.tradinggate.backend.risk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.risk.domain.entity.PnlIntraday;

import java.time.LocalDate;
import java.util.Optional;

public interface PnlIntradayRepository extends JpaRepository<PnlIntraday, Long> {

  Optional<PnlIntraday> findByBusinessDateAndAccountIdAndSymbolId(
      LocalDate businessDate, Long accountId, Long symbolId
  );
}
