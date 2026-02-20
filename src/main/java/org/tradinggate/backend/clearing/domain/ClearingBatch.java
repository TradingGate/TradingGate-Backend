package org.tradinggate.backend.clearing.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.domain.e.ClearingFailureCode;

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
                @Index(name = "idx_clearing_batch_status_created", columnList = "status, created_at"),
                @Index(name = "idx_clearing_batch_snapshot_key", columnList = "snapshot_key")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 32)
    private ClearingFailureCode failureCode;

    @Column(name="remark", length = 255)
    private String remark;

    /**
     * 워터마크 스냅샷 키 (결정론적/짧은 ID)
     * - RUNNING 선점 시점에 watermarkOffsets로부터 생성되어 고정된다.
     * - 이벤트/로그/운영에서 참조하기 쉬운 "스냅샷 아이디" 역할.
     */
    @Column(name = "snapshot_key", length = 32)
    private String snapshotKey;

    /**
     * partition -> last_processed_offset (정합성 기준점)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="cutoff_offsets", nullable = false, columnDefinition = "jsonb")
    private Map<String, Long> cutoffOffsets;

    @Column(name="scope", length = 64)
    private String scope;

    @Column(name="created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name="updated_at", nullable = false)
    private Instant updatedAt;

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

    public void markRunning(String snapshotKey, Map<String, Long> watermarkOffsets) {
        this.status = ClearingBatchStatus.RUNNING;
        this.startedAt = Instant.now();
        this.snapshotKey = snapshotKey;
        this.cutoffOffsets = watermarkOffsets;
        this.failureCode = null;
        this.remark = null;
    }

    public void markSuccess() {
        this.status = ClearingBatchStatus.SUCCESS;
        this.finishedAt = Instant.now();
        this.failureCode = null;
        this.remark = null;
    }

    public void markFailed(ClearingFailureCode failureCode, String remark) {
        this.status = ClearingBatchStatus.FAILED;
        this.finishedAt = Instant.now();
        this.failureCode = failureCode;
        this.remark = truncate(remark, 255);
    }

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

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
