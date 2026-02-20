package org.tradinggate.backend.clearing.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.ClearingResult;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public interface ClearingBatchRepository extends JpaRepository<ClearingBatch, Long> {

    Optional<ClearingBatch> findByBusinessDateAndBatchTypeAndRunKeyAndAttempt(
            LocalDate businessDate,
            ClearingBatchType batchType,
            String runKey,
            int attempt
    );

    @Query("select b.status from ClearingBatch b where b.id = :batchId")
    Optional<ClearingBatchStatus> findStatusById(@Param("batchId") Long batchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ClearingBatch b
           set b.status = :running,
               b.startedAt = :startedAt,
               b.cutoffOffsets = :cutoffOffsets,
               b.marketSnapshotId = :marketSnapshotId
         where b.id = :batchId
           and b.status = :pending
    """)
    int tryMarkRunning(
            @Param("batchId") Long batchId,
            @Param("pending") ClearingBatchStatus pending,
            @Param("running") ClearingBatchStatus running,
            @Param("startedAt") Instant startedAt,
            @Param("cutoffOffsets") Map<String, Long> cutoffOffsets,
            @Param("marketSnapshotId") Long marketSnapshotId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ClearingBatch b
           set b.status = :success,
               b.finishedAt = :finishedAt,
               b.remark = null
         where b.id = :batchId
    """)
    int markSuccess(@Param("batchId") Long batchId,
                    @Param("success") ClearingBatchStatus success,
                    @Param("finishedAt") Instant finishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ClearingBatch b
           set b.status = :failed,
               b.finishedAt = :finishedAt,
               b.remark = :remark
         where b.id = :batchId
    """)
    int markFailed(@Param("batchId") Long batchId,
                   @Param("failed") ClearingBatchStatus failed,
                   @Param("finishedAt") Instant finishedAt,
                   @Param("remark") String remark);

    Page<ClearingBatch> findByStatusAndCreatedAtAfterOrderByCreatedAtAsc(
            ClearingBatchStatus status,
            Instant createdAt,
            Pageable pageable
    );

    Optional<ClearingBatch> findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(
            LocalDate businessDate,
            ClearingBatchType batchType,
            String scope
    );
}
