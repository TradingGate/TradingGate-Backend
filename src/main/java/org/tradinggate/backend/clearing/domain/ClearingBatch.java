package org.tradinggate.backend.clearing.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(
        name = "clearing_batch",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_clearing_batch_business_type",
                        columnNames = {"business_date", "batch_type", "run_key", "attempt"}
                )
        },
        indexes = {
                @Index(name = "idx_clearing_batch_business_type_run", columnList = "business_date, batch_type, run_key"),
                @Index(name = "idx_clearing_batch_status_created", columnList = "status, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ClearingBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name="batch_type", nullable = false, length = 16)
    private ClearingBatchType batchType;

    @Column(name = "run_key", nullable = false, length = 32)
    private String runKey;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "retry_of_batch_id")
    private Long retryOfBatchId;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false, length = 16)
    private ClearingBatchStatus status;

    @Column(name="started_at")
    private Instant startedAt;

    @Column(name="finished_at")
    private Instant finishedAt;

    @Column(name="remark", length = 255)
    private String remark;

    /**
     * Kafka partition -> offset
     * e.g. {"0":1234,"1":5678}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="cutoff_offsets", nullable = false, columnDefinition = "jsonb")
    private Map<String, Long> cutoffOffsets;

    @Column(name="market_snapshot_id")
    private Long marketSnapshotId;

    @Column(name="scope", length = 64)
    private String scope;

    @Column(name="created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name="updated_at", nullable = false)
    private Instant updatedAt;

    // === Factory methods ===
    public static ClearingBatch pending(LocalDate businessDate, ClearingBatchType type, String runKey, int attempt, String scope) {
        return ClearingBatch.builder()
                .businessDate(businessDate)
                .batchType(type)
                .runKey(runKey)
                .attempt(attempt)
                .scope(scope)
                .status(ClearingBatchStatus.PENDING)
                .cutoffOffsets(Map.of())
                .build();
    }

    // === Domain state transition methods ===
    public void markRunning(Map<String, Long> cutoffOffsets, Long marketSnapshotId) {
        this.status = ClearingBatchStatus.RUNNING;
        this.startedAt = Instant.now();
        this.cutoffOffsets = cutoffOffsets;
        this.marketSnapshotId = marketSnapshotId;
    }

    public void markSuccess() {
        this.status = ClearingBatchStatus.SUCCESS;
        this.finishedAt = Instant.now();
        this.remark = null;
    }

    public void markFailed(String remark) {
        this.status = ClearingBatchStatus.FAILED;
        this.finishedAt = Instant.now();
        this.remark = truncate(remark, 255);
    }

    // === JPA lifecycle callbacks ===
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = ClearingBatchStatus.PENDING;
        if (this.cutoffOffsets == null) this.cutoffOffsets = Map.of();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // === Internal helpers ===
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
