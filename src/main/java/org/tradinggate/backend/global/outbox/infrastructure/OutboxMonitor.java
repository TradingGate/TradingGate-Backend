package org.tradinggate.backend.global.outbox.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.global.outbox.domain.OutboxStatus;

import java.time.Duration;

@Log4j2
@Component
@RequiredArgsConstructor
public class OutboxMonitor {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties outboxProperties;

    /**
     * 운영 감시용 파라미터(필요하면 properties로 빼도 됨)
     */
    private final long warnPendingCount = 10_000;
    private final long warnFailedCount = 100;
    private final Duration stuckThreshold = Duration.ofSeconds(60);

    @Scheduled(fixedDelayString = "${outbox.monitor-interval-ms:5000}")
    public void check() {
        long pending = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        long failed = outboxEventRepository.countByStatus(OutboxStatus.FAILED);
        long pendingOld = outboxEventRepository.countPendingOlderThan(java.time.Instant.now().minus(stuckThreshold));

        if (pending >= warnPendingCount || failed >= warnFailedCount || pendingOld > 0) {
            log.warn("[OUTBOX][MONITOR] backlog detected. pending={}, failed={}, pendingOld(>{}s)={}, publishBatchSize={}, pollMs={}",
                    pending, failed, stuckThreshold.toSeconds(), pendingOld,
                    outboxProperties.getBatchSize(), outboxProperties.getPollIntervalMs());
        } else {
            // 너무 시끄러우면 이 로그는 제거/디버그로
            log.debug("[OUTBOX][MONITOR] ok. pending={}, failed={}", pending, failed);
        }
    }
}
