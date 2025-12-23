package org.tradinggate.backend.matching.engine.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.matching.engine.model.OrderCommand;
import org.tradinggate.backend.matching.engine.service.MatchingWorkerService;
import org.tradinggate.backend.matching.snapshot.SnapshotCoordinator;
import org.tradinggate.backend.matching.snapshot.shutdown.PartitionOffsetTracker;

import static org.tradinggate.backend.matching.engine.util.KafkaJsonUtil.parseOrderCommand;

@Log4j2
@Component
@Profile("worker")
@RequiredArgsConstructor
public class OrdersInKafkaListener {

    private final MatchingWorkerService matchingWorkerService;
    private final SnapshotCoordinator snapshotCoordinator;
    private final PartitionOffsetTracker tracker;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${tradinggate.matching.orders-in-topic}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrdersIn(
            String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        long nowMillis = System.currentTimeMillis();

        try {
            OrderCommand command = parseOrderCommand(objectMapper, payload);

            // 심볼이 없거나 잘못된 경우는 그냥 버린다. (향후 REJECT 이벤트로 바꿀 수 있음)
            if (command.getSymbol() == null || command.getSymbol().isBlank()) {
                log.warn("Invalid symbol in orders.in message. topic={}, partition={}, offset={}, payload={}",
                        topic, partition, offset, payload);
                ack.acknowledge();
                return;
            }

            matchingWorkerService.handle(command, nowMillis, topic, partition, offset);

            ack.acknowledge();

            tracker.markProcessed(topic, partition, offset);

            snapshotCoordinator.afterRecordProcessed(
                    topic,
                    partition,
                    offset,
                    nowMillis
            );

        } catch (Exception e) {
            log.error("Failed to handle orders.in message. topic={}, partition={}, offset={}, payload={}",
                    topic, partition, offset, payload, e);

            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }
}
