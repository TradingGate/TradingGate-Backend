package org.tradinggate.backend.global.outbox.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.global.outbox.domain.OutboxEvent;
import org.tradinggate.backend.global.outbox.domain.OutboxStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * PENDING 이벤트를 락 걸고 가져온다.
     * - 반드시 @Transactional 컨텍스트에서 호출해야 함.
     * - PostgreSQL 전용: FOR UPDATE SKIP LOCKED
     */
    @Query(
            value = """
                SELECT *
                FROM outbox_event
                WHERE status = 'PENDING'
                ORDER BY created_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true
    )
    List<OutboxEvent> lockAndLoadPending(@Param("limit") int limit);
    // ---- 운영 조회용 ----

    long countByStatus(OutboxStatus status);

    @Query("select count(e) from OutboxEvent e where e.status = 'PENDING' and e.createdAt < :threshold")
    long countPendingOlderThan(@Param("threshold") Instant threshold);

    @Query("select e from OutboxEvent e where e.status = :status order by e.createdAt asc")
    List<OutboxEvent> findOldestByStatus(@Param("status") OutboxStatus status, Pageable pageable);

    Optional<OutboxEvent> findByIdempotencyKey(String idempotencyKey);

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
               e.lastError = null
         where e.status = 'FAILED'
    """)
    int resetAllFailedToPending();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEvent e
           set e.status = 'PENDING',
               e.lastError = null
         where e.status = 'FAILED' and e.createdAt >= :from
    """)
    int resetFailedSince(@Param("from") Instant from);

    @Query(value = "select count(*) from outbox_event where idempotency_key like concat(?1, '%')", nativeQuery = true)
    long countByIdempotencyKeyPrefix(String prefix);
}
