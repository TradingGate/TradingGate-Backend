package org.tradinggate.backend.settlementIntegrity.recon.domain;

import jakarta.persistence.*;
import lombok.*;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconBatchStatus;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconFailureCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "recon_batch",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_recon_batch_clearing_attempt",
                        columnNames = {"clearing_batch_id", "attempt"}
                )
        },
        indexes = {
                @Index(name = "idx_recon_batch_status_created", columnList = "status, created_at"),
                @Index(name = "idx_recon_batch_clearing", columnList = "clearing_batch_id"),
                @Index(name = "idx_recon_batch_snapshot_key", columnList = "snapshot_key")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clearing_batch_id", nullable = false)
    private Long clearingBatchId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    /**
     * 운영/추적 편의용 (ClearingBatch.snapshotKey 복사)
     * - recon 결과를 사람이 찾기 쉽게.
     */
    @Column(name = "snapshot_key", length = 32)
    private String snapshotKey;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "retry_of_batch_id")
    private Long retryOfBatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReconBatchStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 32)
    private ReconFailureCode failureCode;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "diff_count", nullable = false)
    private long diffCount;

    @Column(name = "high_severity_count", nullable = false)
    private long highSeverityCount;

    @Column(name = "total_abs_diff", precision = 36, scale = 18)
    private BigDecimal totalAbsDiff;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // === Factory ===
    public static ReconBatch pending(Long clearingBatchId, LocalDate businessDate, String snapshotKey, int attempt) {
        return ReconBatch.builder()
                .clearingBatchId(clearingBatchId)
                .businessDate(businessDate)
                .snapshotKey(snapshotKey)
                .attempt(attempt)
                .status(ReconBatchStatus.PENDING)
                .build();
    }

    public static ReconBatch retryPendingOf(ReconBatch baseBatch, int nextAttempt) {
        return ReconBatch.builder()
                .clearingBatchId(baseBatch.getClearingBatchId())
                .businessDate(baseBatch.getBusinessDate())
                .snapshotKey(baseBatch.getSnapshotKey())
                .attempt(nextAttempt)
                .retryOfBatchId(baseBatch.getId())
                .status(ReconBatchStatus.PENDING)
                .build();
    }

    // === State ===
    public void markRunning() {
        this.status = ReconBatchStatus.RUNNING;
        this.startedAt = Instant.now();
        this.failureCode = null;
        this.remark = null;
    }

    public void markSuccess() {
        this.status = ReconBatchStatus.SUCCESS;
        this.finishedAt = Instant.now();
        this.failureCode = null;
        this.remark = null;
    }

    public void markSuccessWithSummary(long diffCount, long highSeverityCount, BigDecimal totalAbsDiff) {
        this.status = ReconBatchStatus.SUCCESS;
        this.finishedAt = Instant.now();
        this.failureCode = null;
        this.remark = null;
        this.diffCount = Math.max(0L, diffCount);
        this.highSeverityCount = Math.max(0L, highSeverityCount);
        this.totalAbsDiff = totalAbsDiff == null ? BigDecimal.ZERO : totalAbsDiff;
    }

    public void markFailed(ReconFailureCode code, String remark) {
        this.status = ReconBatchStatus.FAILED;
        this.finishedAt = Instant.now();
        this.failureCode = code;
        this.remark = truncate(remark, 255);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = ReconBatchStatus.PENDING;
        if (this.attempt <= 0) this.attempt = 1;
        if (this.totalAbsDiff == null) this.totalAbsDiff = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
