package org.tradinggate.backend.recon.domain;

import jakarta.persistence.*;
import lombok.*;
import org.tradinggate.backend.recon.domain.e.ReconBatchStatus;
import org.tradinggate.backend.recon.domain.e.ReconFailureCode;

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
