package org.tradinggate.backend.settlementIntegrity.recon.domain;

import jakarta.persistence.*;
import lombok.*;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconDiffStatus;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconItemType;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconSeverity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "recon_diff",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_recon_diff_batch_account_asset_item",
                        columnNames = {"recon_batch_id", "account_id", "asset", "item_type"}
                )
        },
        indexes = {
                @Index(name = "idx_recon_diff_batch", columnList = "recon_batch_id"),
                @Index(name = "idx_recon_diff_business_account", columnList = "business_date, account_id"),
                @Index(name = "idx_recon_diff_status_severity", columnList = "status, severity, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconDiff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK로 묶고 싶으면 @ManyToOne로 바꿔도 됨. (지금은 단순화)
    @Column(name = "recon_batch_id", nullable = false)
    private Long reconBatchId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "asset", nullable = false, length = 16)
    private String asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 32)
    private ReconItemType itemType;

    @Column(name = "expected_value", precision = 36, scale = 18)
    private BigDecimal expectedValue;

    @Column(name = "actual_value", precision = 36, scale = 18)
    private BigDecimal actualValue;

    @Column(name = "diff_value", precision = 36, scale = 18)
    private BigDecimal diffValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private ReconSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReconDiffStatus status;

    @Column(name = "memo", length = 255)
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ReconDiff open(
            Long reconBatchId,
            LocalDate businessDate,
            Long accountId,
            String asset,
            ReconItemType itemType,
            BigDecimal expected,
            BigDecimal actual,
            BigDecimal diff,
            ReconSeverity severity,
            String memo
    ) {
        return ReconDiff.builder()
                .reconBatchId(reconBatchId)
                .businessDate(businessDate)
                .accountId(accountId)
                .asset(asset == null ? null : asset.toUpperCase())
                .itemType(itemType)
                .expectedValue(expected)
                .actualValue(actual)
                .diffValue(diff)
                .severity(severity)
                .status(ReconDiffStatus.OPEN)
                .memo(memo)
                .build();
    }

    public void markIgnored() {
        this.status = ReconDiffStatus.IGNORED;
    }

    public void markFixed() {
        this.status = ReconDiffStatus.FIXED;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = ReconDiffStatus.OPEN;
        if (this.asset != null) this.asset = this.asset.toUpperCase();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
        if (this.asset != null) this.asset = this.asset.toUpperCase();
    }
}
