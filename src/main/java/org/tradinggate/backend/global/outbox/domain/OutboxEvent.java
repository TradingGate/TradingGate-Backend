package org.tradinggate.backend.global.outbox.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(
        name = "outbox_event",
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
                @Index(name = "idx_outbox_producer_status_created", columnList = "producer_type, status, created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_outbox_idempotency", columnNames = {"idempotency_key"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "producer_type", nullable = false, length = 16)
    private OutboxProducerType producerType;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "idempotency_key", nullable = false, length = 256)
    private String idempotencyKey;

    /**
     * PostgreSQL jsonb 컬럼과 매핑.
     * Hibernate 6 + Postgres에서 @JdbcTypeCode(SqlTypes.JSON) 사용 가능.
     *
     * payload 규약 에시
     * Map<String, Object> payload = Map.of(
     *     "topic", "clearing.settlement",
     *     "key", String.valueOf(accountId),
     *     "body", Map.of(
     *         "eventType", "CLEARING.SETTLEMENT",
     *         "businessDate", businessDate.toString(),
     *         "batchId", batchId,
     *         "accountId", accountId,
     *         "symbolId", symbolId
     *     )
     * );
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    // === Factory methods ===

    public static OutboxEvent pending(
            OutboxProducerType producerType,
            String eventType,
            String aggregateType,
            Long aggregateId,
            String idempotencyKey,
            Map<String, Object> payload
    ) {
        return OutboxEvent.builder()
                .producerType(producerType)
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .idempotencyKey(idempotencyKey)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
    }

    public static OutboxEvent of(
            OutboxProducerType producerType,
            String eventType,
            String aggregateType,
            Long aggregateId,
            String idempotencyKey,
            Map<String, Object> payload,
            OutboxStatus status
    ) {
        return OutboxEvent.builder()
                .producerType(producerType)
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .idempotencyKey(idempotencyKey)
                .payload(payload)
                .status(status)
                .retryCount(0)
                .build();
    }

    public static OutboxEvent pendingWithAggregate(
            OutboxProducerType producerType,
            String eventType,
            String aggregateType,
            Long aggregateId,
            String idempotencyKey,
            Map<String, Object> payload
    ) {
        return pending(producerType, eventType, aggregateType, aggregateId, idempotencyKey, payload);
    }


    // === Domain state transition methods ===

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.lastError = null;
    }

    public void markFailed(String error, int maxRetries) {
        this.retryCount += 1;
        this.lastError = truncate(error, 255);
        if (this.retryCount >= maxRetries) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.status = OutboxStatus.PENDING; // 재시도 대상
        }
    }

    public boolean isPending() {
        return this.status == OutboxStatus.PENDING;
    }

    public boolean isFailed() {
        return this.status == OutboxStatus.FAILED;
    }


    // === JPA lifecycle callbacks ===

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = OutboxStatus.PENDING;
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