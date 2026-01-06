package org.tradinggate.backend.clearing.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.ClearingBatchStatus;
import org.tradinggate.backend.clearing.domain.ClearingBatchType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClearingBatchRepository extends JpaRepository<ClearingBatch, Long> {

    Optional<ClearingBatch> findByBusinessDateAndBatchType(LocalDate businessDate, ClearingBatchType batchType);

    List<ClearingBatch> findTop50ByStatusOrderByCreatedAtAsc(ClearingBatchStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select b
          from ClearingBatch b
         where b.id = :id
    """)
    Optional<ClearingBatch> lockById(@Param("id") Long id);

    boolean existsByBusinessDateAndBatchTypeAndStatus(LocalDate businessDate, ClearingBatchType batchType, ClearingBatchStatus status);
}
