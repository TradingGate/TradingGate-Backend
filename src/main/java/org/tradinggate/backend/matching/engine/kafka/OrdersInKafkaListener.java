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

/**
 * - orders.in 토픽의 단일 레코드를 "엔진 처리 → output 발행 → offset 추적 → 스냅샷 트리거" 흐름으로 연결한다.
 *
 * [중요 규칙]
 * - 정합성 관점에서 "ack"는 엔진 처리 + output 발행이 성공했을 때만 수행해야 한다.
 *   (실패했는데 ack 하면 offset이 커밋되어 재처리가 불가능해짐)
 *
 * [에러 처리 정책]
 * - 예외는 삼키지 않고 던져서 Spring-Kafka ErrorHandler/Backoff 정책으로 재시도되게 한다.
 * - 즉, 이 리스너는 "최소한 한번(at-least-once)" 처리에 기대고, 멱등성/중복 처리 방어는 엔진/이벤트쪽에서 담당한다.
 */
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

            if (command.getSymbol() == null || command.getSymbol().isBlank()) {
                log.warn("Invalid symbol in orders.in message. topic={}, partition={}, offset={}, payload={}", topic, partition, offset, payload);
                ack.acknowledge();
                return;
            }

            // handle 내부에서 output publish까지 완료되어야 처리 완료로 간주한다.
            matchingWorkerService.handle(command, nowMillis, topic, partition, offset);

            ack.acknowledge();

            // revokedAfterCommit에서 스냅샷을 찍을 때 기준 offset으로 사용한다.
            tracker.markProcessed(topic, partition, offset);

            // 스냅샷은 best-effort 비동기 큐에 적재
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
