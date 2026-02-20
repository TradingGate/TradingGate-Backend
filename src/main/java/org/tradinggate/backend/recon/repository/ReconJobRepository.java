package org.tradinggate.backend.recon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.recon.domain.ReconJob;
import org.tradinggate.backend.recon.domain.ReconJobStatus;

import java.util.List;

public interface ReconJobRepository extends JpaRepository<ReconJob, Long> {

    List<ReconJob> findTop50ByStatusOrderByCreatedAtAsc(ReconJobStatus status);
}
