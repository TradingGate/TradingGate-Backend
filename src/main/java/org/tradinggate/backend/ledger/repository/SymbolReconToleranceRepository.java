package org.tradinggate.backend.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.recon.domain.SymbolReconTolerance;

public interface SymbolReconToleranceRepository extends JpaRepository<SymbolReconTolerance, Long> {
}
