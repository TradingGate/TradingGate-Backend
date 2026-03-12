package org.tradinggate.backend.settlementIntegrity.clearing.domain;

import jakarta.persistence.*;
import lombok.*;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingResultStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "clearing_result",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_clearing_result_batch_account_asset",
                        columnNames = {"batch_id", "account_id", "asset"}
                )
        },
        indexes = {
                @Index(name = "idx_clearing_result_business_account_asset", columnList = "business_date, account_id, asset"),
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
    @JoinColumn(
            name = "batch_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_clearing_result_batch")
    )
    private ClearingBatch batch;

    @Column(name="business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name="account_id", nullable = false)
    private Long accountId;

    /**
     * 단일통화면 "KRW" / "USDT" 고정으로 사용.
     * 향후 코인 잔고까지 확장할 때도 동일 스키마로 재사용 가능.
     */
    @Column(name="asset", nullable = false, length = 16)
    private String asset;

    /**
     * 스냅샷 체인을 만들고 싶으면 opening을 채우고,
     * MVP에서 더 단순하게 가려면 null 허용으로 두고 closing만 신뢰해도 된다.
     */
    @Column(name="opening_balance", precision = 36, scale = 18)
    private BigDecimal openingBalance;

    @Column(name="closing_balance", nullable = false, precision = 36, scale = 18)
    private BigDecimal closingBalance;

    @Column(name="net_change", nullable = false, precision = 36, scale = 18)
    private BigDecimal netChange;

    /**
     * 당일 총 수수료(현금 통화 기준). (ledger_entry 집계 결과)
     */
    @Column(name="fee_total", nullable = false, precision = 36, scale = 18)
    private BigDecimal feeTotal;

    /**
     * 당일 체결 건수(계정 관점). trade_id DISTINCT count 권장.
     */
    @Column(name="trade_count", nullable = false)
    private long tradeCount;

    /**
     * 당일 거래대금 합(현금 통화 기준). (예: sum(qty*price) for BUY/SELL)
     */
    @Column(name="trade_value", nullable = false, precision = 36, scale = 18)
    private BigDecimal tradeValue;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false, length = 16)
    private ClearingResultStatus status;

    @Column(name="created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name="updated_at", nullable = false)
    private Instant updatedAt;

    // === Factory methods ===

    /**
     * B-5는 "DB 집계 결과"를 기반으로 Result를 생성한다.
     * preliminary는 배치 처리 중 중간 상태로 저장할 때만 사용(필수는 아님).
     */
    public static ClearingResult preliminary(
            ClearingBatch batch,
            LocalDate businessDate,
            Long accountId,
            String asset
    ) {
        return ClearingResult.builder()
                .batch(batch)
                .businessDate(businessDate)
                .accountId(accountId)
                .asset(asset)
                .status(ClearingResultStatus.PRELIMINARY)
                .closingBalance(BigDecimal.ZERO)
                .openingBalance(null)
                .netChange(BigDecimal.ZERO)
                .feeTotal(BigDecimal.ZERO)
                .tradeCount(0L)
                .tradeValue(BigDecimal.ZERO)
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

        if (this.closingBalance == null) this.closingBalance = BigDecimal.ZERO;
        if (this.netChange == null) this.netChange = BigDecimal.ZERO;
        if (this.feeTotal == null) this.feeTotal = BigDecimal.ZERO;
        if (this.tradeValue == null) this.tradeValue = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
