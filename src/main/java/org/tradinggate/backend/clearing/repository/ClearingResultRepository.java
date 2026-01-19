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

    List<ClearingResult> findByBatchId(Long batchId);

    Optional<ClearingResult> findByBatchIdAndAccountIdAndSymbolId(Long batchId, Long accountId, Long symbolId);

    List<ClearingResult> findByBusinessDateAndAccountId(Long businessDate, Long accountId); // (주의) 타입 맞추기용 아래 제공

    List<ClearingResult> findByBusinessDateAndAccountId(LocalDate businessDate, Long accountId);

    Optional<ClearingResult> findTop1ByAccountIdAndSymbolIdOrderByBusinessDateDesc(Long accountId, Long symbolId);
}
