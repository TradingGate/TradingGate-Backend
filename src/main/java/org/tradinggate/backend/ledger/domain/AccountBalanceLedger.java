package org.tradinggate.backend.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "account_balance_ledger",
        indexes = {
                @Index(name="idx_ledger_business_account", columnList = "business_date, account_id"),
                @Index(name="idx_ledger_account_created", columnList = "account_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AccountBalanceLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name="account_id", nullable = false)
    private Long accountId;

    @Column(name="currency", nullable = false, length = 16)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name="entry_type", nullable = false, length = 32)
    private LedgerEntryType entryType;

    @Column(name="amount", nullable = false, precision = 36, scale = 18)
    private BigDecimal amount;

    @Column(name="ref_type", nullable = false, length = 64)
    private String refType;

    @Column(name="ref_id")
    private Long refId;

    @Column(name="memo", length = 255)
    private String memo;

    @Column(name="created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // === Factory methods ===
    public static AccountBalanceLedger of(
            LocalDate businessDate,
            Long accountId,
            String currency,
            LedgerEntryType entryType,
            BigDecimal amount,
            String refType,
            Long refId,
            String memo
    ) {
        return AccountBalanceLedger.builder()
                .businessDate(businessDate)
                .accountId(accountId)
                .currency(currency)
                .entryType(entryType)
                .amount(amount)
                .refType(refType)
                .refId(refId)
                .memo(memo)
                .build();
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
