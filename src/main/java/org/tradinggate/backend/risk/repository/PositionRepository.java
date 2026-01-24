package org.tradinggate.backend.risk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.tradinggate.backend.risk.domain.entity.Position;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Position> findByAccountIdAndSymbolId(Long accountId, Long symbolId);

  List<Position> findAllByAccountId(Long accountId);
}
