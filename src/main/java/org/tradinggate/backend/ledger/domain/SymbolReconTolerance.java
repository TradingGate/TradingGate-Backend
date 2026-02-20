package org.tradinggate.backend.ledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "symbol_recon_tolerance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SymbolReconTolerance {

    @Id
    @Column(name="symbol_id")
    private Long symbolId;

    @Column(name="qty_tolerance", nullable = false, precision = 36, scale = 18)
    private BigDecimal qtyTolerance;

    @Column(name="pnl_tolerance", nullable = false, precision = 36, scale = 18)
    private BigDecimal pnlTolerance;

    @Column(name="fee_tolerance", nullable = false, precision = 36, scale = 18)
    private BigDecimal feeTolerance;

    @Column(name="updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
