package org.tradinggate.backend.global.outbox.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;
import org.tradinggate.backend.global.outbox.domain.OutboxEvent;
import org.tradinggate.backend.global.outbox.domain.OutboxStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Profile({"clearing", "risk"})
public class OutboxAdminService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublisher outboxPublisher;

    /**
     * 운영: FAILED -> PENDING 리셋 (단건)
     */
    public int resetFailedEvent(Long outboxEventId) {
        return outboxEventRepository.resetToPending(outboxEventId);
    }

    /**
     * 운영: 모든 FAILED -> PENDING 리셋
     */
    public int resetAllFailed() {
        return outboxEventRepository.resetAllFailedToPending();
    }

    /**
     * 운영: 특정 시각 이후 생성된 FAILED만 리셋
     */
    public int resetFailedSince(Instant from) {
        return outboxEventRepository.resetFailedSince(from);
    }

    /**
     * 운영: 즉시 퍼블리싱을 몇 번 루프 돌려서 빨리 비움
     */
    public int drainNow(int loops) {
        int total = 0;
        for (int i = 0; i < loops; i++) {
            int n = outboxPublisher.publishOnce();
            total += n;
            if (n == 0) break;
        }
        return total;
    }

    /**
     * 운영: 현재 큐 상태 요약
     */
    public OutboxQueueSnapshot snapshot(Duration pendingStuckThreshold) {
        long pending = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        long failed = outboxEventRepository.countByStatus(OutboxStatus.FAILED);
        long sent = outboxEventRepository.countByStatus(OutboxStatus.SENT);

        Instant threshold = Instant.now().minus(pendingStuckThreshold);
        long pendingOld = outboxEventRepository.countPendingOlderThan(threshold);

        List<OutboxEvent> oldestPending = outboxEventRepository.findOldestByStatus(
                OutboxStatus.PENDING, PageRequest.of(0, 5)
        );

        return new OutboxQueueSnapshot(pending, failed, sent, pendingOld, threshold, oldestPending);
    }

    public record OutboxQueueSnapshot(
            long pendingCount,
            long failedCount,
            long sentCount,
            long pendingOlderThanThresholdCount,
            Instant pendingThresholdInstant,
            List<OutboxEvent> oldestPending
    ) {}
}
