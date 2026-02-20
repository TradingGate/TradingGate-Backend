package org.tradinggate.backend.clearing.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.tradinggate.backend.clearing.domain.ClearingResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClearingResultRepository extends JpaRepository<ClearingResult, Long> {

    long countByBatch_Id(Long batchId);

    Page<ClearingResult> findByBatchId(Long batchId, Pageable pageable);

}
