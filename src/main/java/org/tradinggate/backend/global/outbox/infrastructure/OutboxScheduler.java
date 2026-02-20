package org.tradinggate.backend.global.outbox.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.global.outbox.service.OutboxPublisher;

@Log4j2
@Component
@RequiredArgsConstructor
@Profile({"clearing", "risk"})
public class OutboxScheduler {

    private final OutboxPublisher outboxPublisher;

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:200}")
    public void tick() {
        try {
            outboxPublisher.publishOnce();
        } catch (Exception e) {
            log.error("[OUTBOX] tick failed", e);
        }
    }
}
