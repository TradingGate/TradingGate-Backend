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


@Log4j2
@Component
@RequiredArgsConstructor
public class SnapshotCoordinator {

    private final OrderBookRegistry orderBookRegistry;
    private final SnapshotAssembler partitionSnapshotAssembler;
    private final SnapshotWriteQueue snapshotWriteQueue;

    private final Map<Integer, SnapshotPolicy> policyByPartition = new ConcurrentHashMap<>();

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

        if (offered) {
            policy.onSnapshotTaken(nowMillis);
        }
    }

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
