package org.tradinggate.backend.risk.repository.trade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.risk.domain.entity.trade.ProcessedTrade;

public interface ProcessedTradeRepository extends JpaRepository<ProcessedTrade, Long> {

  boolean existsByTradeId(Long tradeId);
}
