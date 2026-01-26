package org.tradinggate.backend.recon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.recon.domain.ReconFixLog;

import java.util.List;

public interface ReconFixLogRepository extends JpaRepository<ReconFixLog, Long> {

    List<ReconFixLog> findByReconMismatchIdOrderByFixedAtAsc(Long reconMismatchId);
}
