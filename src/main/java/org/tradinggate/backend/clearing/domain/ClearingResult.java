package org.tradinggate.backend.clearing.domain;

import jakarta.persistence.*;
import lombok.*;
import org.tradinggate.backend.clearing.domain.e.ClearingResultStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "clearing_result",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_clearing_result_batch_account_symbol",
                        columnNames = {"batch_id", "account_id", "symbol_id"}
                )
        },
        indexes = {
                @Index(name = "idx_clearing_result_business_account_symbol", columnList = "business_date, account_id, symbol_id"),
                @Index(name = "idx_clearing_result_batch", columnList = "batch_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ClearingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_clearing_result_batch"))
    private ClearingBatch batch;

    @Column(name="business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name="account_id", nullable = false)
    private Long accountId;

    @Column(name="symbol_id", nullable = false)
    private Long symbolId;

    @Column(name="opening_qty", nullable = false, precision = 36, scale = 18)
    private BigDecimal openingQty;

    @Column(name="closing_qty", nullable = false, precision = 36, scale = 18)
    private BigDecimal closingQty;

    @Column(name="opening_price", precision = 18, scale = 8)
    private BigDecimal openingPrice;

    @Column(name="closing_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal closingPrice;

    @Column(name="realized_pnl", nullable = false, precision = 18, scale = 8)
    private BigDecimal realizedPnl;

    @Column(name="unrealized_pnl", nullable = false, precision = 18, scale = 8)
    private BigDecimal unrealizedPnl;

    @Column(name="fee", nullable = false, precision = 18, scale = 8)
    private BigDecimal fee;

    @Column(name="funding", precision = 18, scale = 8)
    private BigDecimal funding;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false, length = 16)
    private ClearingResultStatus status;

    @Column(name="created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name="updated_at", nullable = false)
    private Instant updatedAt;

    // === Factory methods ===
    public static ClearingResult preliminary(
            ClearingBatch batch,
            LocalDate businessDate,
            Long accountId,
            Long symbolId
    ) {
        return ClearingResult.builder()
                .batch(batch)
                .businessDate(businessDate)
                .accountId(accountId)
                .symbolId(symbolId)
                .status(ClearingResultStatus.PRELIMINARY)
                .realizedPnl(BigDecimal.ZERO)
                .unrealizedPnl(BigDecimal.ZERO)
                .fee(BigDecimal.ZERO)
                .build();
    }

    public void finalizeAsEod() {
        this.status = ClearingResultStatus.FINAL;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.realizedPnl == null) this.realizedPnl = BigDecimal.ZERO;
        if (this.unrealizedPnl == null) this.unrealizedPnl = BigDecimal.ZERO;
        if (this.fee == null) this.fee = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
