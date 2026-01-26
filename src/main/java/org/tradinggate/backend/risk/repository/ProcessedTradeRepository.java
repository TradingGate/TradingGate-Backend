package org.tradinggate.backend.risk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.risk.domain.entity.ProcessedTrade;

public interface ProcessedTradeRepository extends JpaRepository<ProcessedTrade, Long> {

  boolean existsByTradeId(Long tradeId);
}
