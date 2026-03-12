package org.tradinggate.backend.settlementIntegrity.recon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconBatchStatus;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconFailureCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface ReconBatchRepository extends JpaRepository<ReconBatch, Long> {

    Optional<ReconBatch> findByClearingBatchIdAndAttempt(Long clearingBatchId, int attempt);

    Optional<ReconBatch> findTopByClearingBatchIdOrderByAttemptDesc(Long clearingBatchId);

    Optional<ReconBatch> findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(Long clearingBatchId, LocalDate businessDate);

    @Query("select b.status from ReconBatch b where b.id = :batchId")
    Optional<ReconBatchStatus> findStatusById(@Param("batchId") Long batchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ReconBatch b
           set b.status = :running,
               b.startedAt = :startedAt,
               b.failureCode = null,
               b.remark = null
         where b.id = :batchId
           and b.status = :pending
    """)
    int tryMarkRunning(
            @Param("batchId") Long batchId,
            @Param("pending") ReconBatchStatus pending,
            @Param("running") ReconBatchStatus running,
            @Param("startedAt") Instant startedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ReconBatch b
           set b.status = :success,
               b.finishedAt = :finishedAt,
               b.failureCode = null,
               b.remark = null
         where b.id = :batchId
    """)
    int markSuccess(
            @Param("batchId") Long batchId,
            @Param("success") ReconBatchStatus success,
            @Param("finishedAt") Instant finishedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ReconBatch b
           set b.status = :success,
               b.finishedAt = :finishedAt,
               b.failureCode = null,
               b.remark = null,
               b.diffCount = :diffCount,
               b.highSeverityCount = :highSeverityCount,
               b.totalAbsDiff = :totalAbsDiff
         where b.id = :batchId
    """)
    int markSuccessWithSummary(
            @Param("batchId") Long batchId,
            @Param("success") ReconBatchStatus success,
            @Param("finishedAt") Instant finishedAt,
            @Param("diffCount") long diffCount,
            @Param("highSeverityCount") long highSeverityCount,
            @Param("totalAbsDiff") BigDecimal totalAbsDiff
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ReconBatch b
           set b.status = :failed,
               b.finishedAt = :finishedAt,
               b.failureCode = :failureCode,
               b.remark = :remark
         where b.id = :batchId
    """)
    int markFailed(
            @Param("batchId") Long batchId,
            @Param("failed") ReconBatchStatus failed,
            @Param("finishedAt") Instant finishedAt,
            @Param("failureCode") ReconFailureCode failureCode,
            @Param("remark") String remark
    );
}
