package org.tradinggate.backend.settlementIntegrity.clearing.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingFailureCode;

import java.time.Instant;
import java.time.LocalDate;
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
    @Query(value = """
        with wm as (
            select cb.id as batch_id,
                   coalesce((
                       select max(le.id)
                         from ledger_entry le
                        where le.created_at < (cb.business_date + interval '1 day')
                   ), 0) as max_ledger_id
              from clearing_batch cb
             where cb.id = :batchId
               and cb.status = :pendingStatus
        )
        update clearing_batch cb
           set status = :runningStatus,
               started_at = :startedAt,
               snapshot_key = 'WM-' || substr(md5('max_ledger_id=' || wm.max_ledger_id::text), 1, 12),
               cutoff_offsets = jsonb_build_object('max_ledger_id', wm.max_ledger_id),
               failure_code = null,
               remark = null
          from wm
         where cb.id = wm.batch_id
        """, nativeQuery = true)
    int tryMarkRunningWithDbWatermark(
            @Param("batchId") Long batchId,
            @Param("pendingStatus") String pendingStatus,
            @Param("runningStatus") String runningStatus,
            @Param("startedAt") Instant startedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ClearingBatch b
           set b.status = :success,
               b.finishedAt = :finishedAt,
               b.failureCode = null,
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
               b.failureCode = :failureCode,
               b.remark = :remark
         where b.id = :batchId
    """)
    int markFailed(@Param("batchId") Long batchId,
                   @Param("failed") ClearingBatchStatus failed,
                   @Param("finishedAt") Instant finishedAt,
                   @Param("failureCode") ClearingFailureCode failureCode,
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

    Optional<ClearingBatch> findTopByBusinessDateAndBatchTypeAndStatusAndScopeOrderByIdDesc(
            LocalDate businessDate,
            ClearingBatchType batchType,
            ClearingBatchStatus status,
            String scope
    );
}
