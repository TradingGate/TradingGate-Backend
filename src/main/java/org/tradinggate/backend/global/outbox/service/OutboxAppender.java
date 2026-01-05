package org.tradinggate.backend.global.outbox.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;
import org.tradinggate.backend.global.outbox.domain.OutboxEvent;
import org.tradinggate.backend.global.outbox.domain.OutboxProducerType;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class OutboxAppender {

    private final OutboxEventRepository outboxEventRepository;

    public void append(
            OutboxProducerType producerType,
            String eventType,
            String aggregateType,
            Long aggregateId,
            String idempotencyKey,
            Map<String, Object> payload
    ) {
        OutboxEvent event = OutboxEvent.pending(
                producerType,
                eventType,
                aggregateType,
                aggregateId,
                idempotencyKey,
                payload
        );

        try {
            outboxEventRepository.save(event);
        } catch (DataIntegrityViolationException dup) {
            // idempotency_key UNIQUE 충돌 => 이미 적재된 이벤트로 간주하고 무시(멱등)
        }
    }
}
