package org.tradinggate.backend.risk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.risk.domain.entity.UserRiskProfile;

public interface UserRiskProfileRepository extends JpaRepository<UserRiskProfile, Long> {
}