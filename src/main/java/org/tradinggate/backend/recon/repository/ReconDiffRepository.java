package org.tradinggate.backend.recon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.recon.domain.ReconDiff;

import java.util.List;

public interface ReconDiffRepository extends JpaRepository<ReconDiff, Long> {

    long countByReconBatchId(Long reconBatchId);

    Page<ReconDiff> findByReconBatchId(Long reconBatchId, Pageable pageable);

    @Query("""
        select d
          from ReconDiff d
         where d.reconBatchId = :reconBatchId
           and d.status = 'OPEN'
         order by d.severity desc, d.createdAt asc
    """)
    List<ReconDiff> findOpenDiffs(@Param("reconBatchId") Long reconBatchId, Pageable pageable);
}
