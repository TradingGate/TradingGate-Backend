package org.tradinggate.backend.global.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.global.outbox.domain.OutboxStatus;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;
import org.tradinggate.backend.global.outbox.domain.OutboxEvent;
import org.tradinggate.backend.global.outbox.domain.OutboxProducerType;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Profile({"clearing", "risk"})
public class OutboxAppender {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void append(
            OutboxProducerType producerType,
            String eventType,
            String aggregateType,
            Long aggregateId,
            String idempotencyKey,
            Map<String, Object> payload
    ) {
        // 중복은 정상(멱등)이고, 예외 기반이면 rollback-only 문제가 생길 수 있다.
        final String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("payload serialization failed. idemKey=" + idempotencyKey, e);
        }

        outboxEventRepository.insertIgnoreConflict(
                producerType.name(),
                eventType,
                aggregateType,
                aggregateId,
                idempotencyKey,
                payloadJson,
                OutboxStatus.PENDING.name()
        );
    }
}
