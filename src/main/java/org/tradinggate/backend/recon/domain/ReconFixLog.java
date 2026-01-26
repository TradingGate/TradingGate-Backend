package org.tradinggate.backend.recon.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(
        name = "recon_fix_log",
        indexes = {
                @Index(name = "idx_recon_fix_log_mismatch", columnList = "recon_mismatch_id"),
                @Index(name = "idx_recon_fix_log_fixed_at", columnList = "fixed_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconFixLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recon_mismatch_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_recon_fix_log_mismatch")
    )
    private ReconMismatch reconMismatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private ReconFixAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "jsonb")
    private Map<String, Object> detail;

    @Column(name = "fixed_by", length = 64)
    private String fixedBy;

    @Column(name = "fixed_at")
    private Instant fixedAt;

    // === Factory methods ===
    public static ReconFixLog autoFix(ReconMismatch mismatch, Map<String, Object> detail) {
        return ReconFixLog.builder()
                .reconMismatch(mismatch)
                .action(ReconFixAction.AUTO_FIX)
                .detail(detail)
                .fixedBy("SYSTEM")
                .fixedAt(Instant.now())
                .build();
    }

    public static ReconFixLog manualFix(ReconMismatch mismatch, String operator, Map<String, Object> detail) {
        return ReconFixLog.builder()
                .reconMismatch(mismatch)
                .action(ReconFixAction.MANUAL_FIX)
                .detail(detail)
                .fixedBy(operator)
                .fixedAt(Instant.now())
                .build();
    }

    public static ReconFixLog noAction(ReconMismatch mismatch, String operator, Map<String, Object> detail) {
        return ReconFixLog.builder()
                .reconMismatch(mismatch)
                .action(ReconFixAction.NO_ACTION)
                .detail(detail)
                .fixedBy(operator)
                .fixedAt(Instant.now())
                .build();
    }
}
