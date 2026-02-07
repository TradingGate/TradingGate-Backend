package org.tradinggate.backend.risk.repository.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.risk.domain.entity.risk.RiskState;
import org.tradinggate.backend.risk.domain.entity.risk.RiskStatus;
import java.util.List;
import java.util.Optional;

/**
 * RiskState Repository
 */
public interface RiskStateRepository extends JpaRepository<RiskState, Long> {

  /**
   * 계정 ID로 조회
   */
  Optional<RiskState> findById(Long accountId);

  /**
   * 계정 ID 존재 여부
   */
  boolean existsById(Long accountId);

  /**
   * 특정 상태의 계정들 조회
   */
  List<RiskState> findByStatus(RiskStatus status);

  /**
   * 차단된 계정들 조회
   */
  default List<RiskState> findBlockedAccounts() {
    return findByStatus(RiskStatus.BLOCKED);
  }

  /**
   * 정상 계정들 조회
   */
  default List<RiskState> findNormalAccounts() {
    return findByStatus(RiskStatus.NORMAL);
  }
}
