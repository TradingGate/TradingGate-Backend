package org.tradinggate.backend.global.outbox.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.global.outbox.domain.OutboxEvent;
import org.tradinggate.backend.global.outbox.domain.OutboxStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // === Insert (멱등) ===
    @Transactional
    @Modifying
    @Query(value = """
        insert into outbox_event (
            producer_type, event_type, aggregate_type, aggregate_id,
            idempotency_key, payload, status, retry_count, last_error, created_at, updated_at
        ) values (
            :producerType, :eventType, :aggregateType, :aggregateId,
            :idempotencyKey, cast(:payload as jsonb), :status, 0, null, now(), now()
        )
        on conflict (idempotency_key) do nothing
        """, nativeQuery = true)
    int insertIgnoreConflict(
            @Param("producerType") String producerType,
            @Param("eventType") String eventType,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") Long aggregateId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("payload") String payloadJson,
            @Param("status") String status
    );

    // === Publish 대상 로드 ===
    @Transactional(propagation = Propagation.MANDATORY) // FOR UPDATE SKIP LOCKED는 트랜잭션 밖이면 의미가 없음
    @Query(
            value = """
                SELECT *
                FROM outbox_event
                WHERE status = 'PENDING'
                  AND retry_count < :maxRetries
                ORDER BY created_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true
    )
    List<OutboxEvent> lockAndLoadPending(
            @Param("limit") int limit,
            @Param("maxRetries") int maxRetries
    );

    // ---- 운영 조회용 ----
    long countByStatus(OutboxStatus status);

    @Query("select count(e) from OutboxEvent e where e.status = 'PENDING' and e.createdAt < :threshold")
    long countPendingOlderThan(@Param("threshold") Instant threshold);

    @Query("select e from OutboxEvent e where e.status = :status order by e.createdAt asc")
    List<OutboxEvent> findOldestByStatus(@Param("status") OutboxStatus status, Pageable pageable);

    Optional<OutboxEvent> findByIdempotencyKey(String idempotencyKey);

    @Query(value = "select count(*) from outbox_event where idempotency_key like concat(?1, '%')", nativeQuery = true)
    long countByIdempotencyKeyPrefix(String prefix);

    // ---- 운영 조치용(리셋) ----
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEvent e
           set e.status = 'PENDING',
               e.retryCount = 0,
               e.lastError = null
         where e.id = :id
    """)
    int resetToPending(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEvent e
           set e.status = 'PENDING',
               e.retryCount = 0,
               e.lastError = null
         where e.status = 'FAILED'
    """)
    int resetAllFailedToPending();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEvent e
           set e.status = 'PENDING',
               e.retryCount = 0,
               e.lastError = null
         where e.status = 'FAILED' and e.createdAt >= :from
    """)
    int resetFailedSince(@Param("from") Instant from);
}
