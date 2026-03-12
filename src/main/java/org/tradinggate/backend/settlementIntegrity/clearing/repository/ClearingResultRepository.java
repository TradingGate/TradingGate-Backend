package org.tradinggate.backend.settlementIntegrity.clearing.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingResultStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ClearingResultRepository extends JpaRepository<ClearingResult, Long> {

    long countByBatch_Id(Long batchId);

    Page<ClearingResult> findByBatchId(Long batchId, Pageable pageable);

    @Query("""
        select r.closingBalance
          from ClearingResult r
          join r.batch b
         where r.accountId = :accountId
           and upper(r.asset) = upper(:asset)
           and r.businessDate < :businessDate
           and r.status = :resultStatus
           and b.batchType = :batchType
         order by r.businessDate desc, r.id desc
    """)
    List<BigDecimal> findPreviousClosingBalances(
            @Param("businessDate") LocalDate businessDate,
            @Param("accountId") Long accountId,
            @Param("asset") String asset,
            @Param("resultStatus") ClearingResultStatus resultStatus,
            @Param("batchType") ClearingBatchType batchType
    );
}
