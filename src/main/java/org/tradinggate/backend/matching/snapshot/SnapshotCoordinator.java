package org.tradinggate.backend.matching.snapshot;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.utils.Utils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.matching.engine.kafka.TopicPartitionCountProvider;
import org.tradinggate.backend.matching.engine.model.OrderBook;
import org.tradinggate.backend.matching.engine.model.OrderBookRegistry;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteRequest;
import org.tradinggate.backend.matching.snapshot.model.PartitionSnapshot;
import org.tradinggate.backend.matching.snapshot.model.e.ChecksumAlgorithm;
import org.tradinggate.backend.matching.snapshot.model.e.CompressionType;
import org.tradinggate.backend.matching.snapshot.model.e.SnapshotTriggerReason;
import org.tradinggate.backend.matching.snapshot.util.SnapshotAssembler;
import org.tradinggate.backend.matching.snapshot.util.SnapshotCryptoUtils;
import org.tradinggate.backend.matching.snapshot.util.SnapshotPolicy;
import org.tradinggate.backend.matching.snapshot.writer.SnapshotWriteQueue;

/**
 * - 오더북(파티션 단위) 스냅샷 생성 트리거를 관리한다.
 * - afterRecordProcessed: 정책 기반(주기/이벤트 수 등)으로 "스냅샷 요청"을 enqueue한다.
 * - forceSnapshot: shutdown/revoke 등에서 best-effort로 강제 enqueue한다.
 *
 * [주의]
 * - 실제 파일 IO는 SnapshotWriteQueue(단일 writer 스레드 : 비동기)로 위임한다.
 */
@Log4j2
@Component
@RequiredArgsConstructor
@Profile("worker")
public class SnapshotCoordinator {

    private final OrderBookRegistry orderBookRegistry;
    private final SnapshotAssembler partitionSnapshotAssembler;
    private final SnapshotWriteQueue snapshotWriteQueue;

    /**
     * partition 단위 정책 캐시:
     * - 파티션을 잃는 시점(revoke/lost)에는 반드시 clearPolicyForPartition으로 정리해야 메모리 누수 방지.
     */
    private final Map<Integer, SnapshotPolicy> policyByPartition = new ConcurrentHashMap<>();

    /**
     * @param lastProcessedOffset 이 파티션에서 처리 완료(ack 이후)된 마지막 offset
     * @sideEffects 스냅샷 요청이 enqueue될 수 있음 (큐가 가득 차면 drop될 수 있음)
     */
    public void afterRecordProcessed(
            String topic,
            int partition,
            long lastProcessedOffset,
            long nowMillis
    ) {
        if (topic == null || topic.isBlank()) return;
        if (partition  < 0) return;

        SnapshotPolicy policy = policyByPartition.computeIfAbsent(partition, p -> new SnapshotPolicy());
        policy.onEventProcessed();

        Optional<SnapshotTriggerReason> reasonOpt = policy.shouldSnapshot(nowMillis);
        if (reasonOpt.isEmpty()) return;

        SnapshotTriggerReason reason = reasonOpt.get();

        PartitionSnapshot snapshot = buildPartitionSnapshot(topic, partition, lastProcessedOffset, nowMillis, reason);
        if (snapshot == null) return;

        SnapshotWriteRequest req = new SnapshotWriteRequest(
                topic,
                partition,
                lastProcessedOffset,
                nowMillis,
                reason,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                snapshot
        );

        boolean offered = snapshotWriteQueue.enqueue(req);

        // 스냅샷이 실제로 큐에 들어갔을 때만 policy를 "찍은 것"으로 갱신.
        if (offered) {
            policy.onSnapshotTaken(nowMillis);
        }
    }

    /**
     * shutdown/revoke 등에서 호출되는 강제 스냅샷.
     * - enqueueForce는 "accepting=false" 상태에서도 시도할 수 있으나, 큐가 가득 차면 drop될 수 있다.
     */
    public void forceSnapshot(
            String topic,
            int partition,
            long lastProcessedOffset,
            long nowMillis
    ) {
        if (topic == null || topic.isBlank()) return;
        if (partition < 0) return;

        PartitionSnapshot snapshot = buildPartitionSnapshot(topic, partition, lastProcessedOffset, nowMillis, SnapshotTriggerReason.SHUTDOWN_HOOK);
        if (snapshot == null) return;

        SnapshotWriteRequest req = new SnapshotWriteRequest(
                topic,
                partition,
                lastProcessedOffset,
                nowMillis,
                SnapshotTriggerReason.SHUTDOWN_HOOK,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                snapshot
        );

        snapshotWriteQueue.enqueueForce(req);
    }

    private PartitionSnapshot buildPartitionSnapshot(
            String topic,
            int partition,
            long lastProcessedOffset,
            long nowMillis,
            SnapshotTriggerReason reason
    ) {
        Map<String, OrderBook> inPartition = orderBookRegistry.findAllByPartition(topic, partition);

        if (inPartition.isEmpty()) {
            log.debug("[SNAPSHOT] skip (no orderbook in partition) topic={}, partition={}", topic, partition);
            return null;
        }

        String snapshotId = UUID.randomUUID().toString();

        return partitionSnapshotAssembler.toPartitionSnapshot(
                snapshotId,
                topic,
                partition,
                lastProcessedOffset,
                nowMillis,
                reason,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                inPartition
        );
    }

    public void clearPolicyForPartition(int partition) {
        policyByPartition.remove(partition);
    }
}
