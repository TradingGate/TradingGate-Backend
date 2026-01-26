package org.tradinggate.backend.recon.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "recon_job",
        indexes = {
                @Index(name = "idx_recon_job_status_created", columnList = "status, created_at"),
                @Index(name = "idx_recon_job_date_range", columnList = "business_date_from, business_date_to")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date_from", nullable = false)
    private LocalDate businessDateFrom;

    @Column(name = "business_date_to", nullable = false)
    private LocalDate businessDateTo;

    @Column(name = "scope", nullable = false, length = 64)
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReconJobStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    // === Factory methods ===
    public static ReconJob pending(LocalDate from, LocalDate to, String scope) {
        return ReconJob.builder()
                .businessDateFrom(from)
                .businessDateTo(to)
                .scope(scope)
                .status(ReconJobStatus.PENDING)
                .build();
    }

    // === Domain state transition methods ===
    public void markRunning() {
        this.status = ReconJobStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void markSuccess() {
        this.status = ReconJobStatus.SUCCESS;
        this.finishedAt = Instant.now();
    }

    public void markFailed() {
        this.status = ReconJobStatus.FAILED;
        this.finishedAt = Instant.now();
    }

    public boolean isRunning() {
        return this.status == ReconJobStatus.RUNNING;
    }

    // === JPA lifecycle callbacks ===
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        if (this.status == null) this.status = ReconJobStatus.PENDING;
    }
}
