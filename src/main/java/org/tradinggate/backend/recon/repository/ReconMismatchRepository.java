package org.tradinggate.backend.recon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.recon.domain.ReconMismatch;
import org.tradinggate.backend.recon.domain.ReconMismatchStatus;

import java.time.LocalDate;
import java.util.List;

public interface ReconMismatchRepository extends JpaRepository<ReconMismatch, Long> {

    List<ReconMismatch> findByReconJobId(Long reconJobId);

    List<ReconMismatch> findByStatusOrderByCreatedAtAsc(ReconMismatchStatus status);

    List<ReconMismatch> findByBusinessDateAndAccountId(LocalDate businessDate, Long accountId);

    List<ReconMismatch> findByReconJobIdAndStatus(Long reconJobId, ReconMismatchStatus status);

    long countByStatus(ReconMismatchStatus status);
}
