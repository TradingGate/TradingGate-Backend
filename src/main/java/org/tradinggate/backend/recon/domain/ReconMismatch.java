package org.tradinggate.backend.recon.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;


@Entity
@Table(
        name = "recon_mismatch",
        indexes = {
                @Index(name = "idx_recon_mismatch_job", columnList = "recon_job_id"),
                @Index(name = "idx_recon_mismatch_business_account", columnList = "business_date, account_id"),
                @Index(name = "idx_recon_mismatch_status_severity", columnList = "status, severity, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconMismatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recon_job_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_recon_mismatch_job")
    )
    private ReconJob reconJob;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "symbol_id")
    private Long symbolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 32)
    private ReconItemType itemType;

    @Column(name = "expected_value", nullable = false, precision = 36, scale = 18)
    private BigDecimal expectedValue;

    @Column(name = "actual_value", nullable = false, precision = 36, scale = 18)
    private BigDecimal actualValue;

    @Column(name = "diff_value", nullable = false, precision = 36, scale = 18)
    private BigDecimal diffValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private ReconSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReconMismatchStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // === Factory methods ===
    public static ReconMismatch open(
            ReconJob reconJob,
            LocalDate businessDate,
            Long accountId,
            Long symbolId,
            ReconItemType itemType,
            BigDecimal expectedValue,
            BigDecimal actualValue,
            BigDecimal diffValue,
            ReconSeverity severity
    ) {
        return ReconMismatch.builder()
                .reconJob(reconJob)
                .businessDate(businessDate)
                .accountId(accountId)
                .symbolId(symbolId)
                .itemType(itemType)
                .expectedValue(expectedValue)
                .actualValue(actualValue)
                .diffValue(diffValue)
                .severity(severity)
                .status(ReconMismatchStatus.OPEN)
                .build();
    }

    // === Domain state transition methods ===
    public void markFixed() {
        this.status = ReconMismatchStatus.FIXED;
    }

    public void markIgnored() {
        this.status = ReconMismatchStatus.IGNORED;
    }

    public boolean isOpen() {
        return this.status == ReconMismatchStatus.OPEN;
    }

    // === JPA lifecycle callbacks ===
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = ReconMismatchStatus.OPEN;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
