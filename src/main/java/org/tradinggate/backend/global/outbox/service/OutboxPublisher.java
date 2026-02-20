package org.tradinggate.backend.global.outbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.global.outbox.infrastructure.KafkaOutboxMessageSender;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxProperties;
import org.tradinggate.backend.global.outbox.domain.OutboxEvent;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
@Profile({"clearing", "risk"})
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaOutboxMessageSender outboxMessageSender;
    private final OutboxProperties outboxProperties;

    /**
     * 한 번 호출에 batchSize만큼 처리.
     * - @Transactional 안에서 SELECT FOR UPDATE SKIP LOCKED로 가져오고,
     * - 엔티티 상태 변경(dirty checking)으로 상태 업데이트.
     */
    @Transactional
    public int publishOnce() {
        int batchSize = outboxProperties.getBatchSize();
        int maxRetries = outboxProperties.getMaxRetries();

        List<OutboxEvent> events = outboxEventRepository.lockAndLoadPending(batchSize, outboxProperties.getMaxRetries());
        if (events.isEmpty()) {
            return 0;
        }

        int success = 0;
        for (OutboxEvent event : events) {
            try {
                outboxMessageSender.send(event);
                event.markSent();
                success++;
            } catch (Exception e) {
                String err = summarizeError(e);
                log.warn("[OUTBOX] publish failed. id={}, type={}, idemKey={}, err={}",
                        event.getId(), event.getEventType(), event.getIdempotencyKey(), err);

                event.markPublishFailed(err, maxRetries);
            }
        }
        return success;
    }

    private String summarizeError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) msg = "";
        return e.getClass().getSimpleName() + ": " + msg;
    }
}
