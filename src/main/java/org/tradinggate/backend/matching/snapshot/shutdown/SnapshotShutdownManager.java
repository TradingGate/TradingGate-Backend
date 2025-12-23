package org.tradinggate.backend.matching.snapshot.shutdown;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.matching.snapshot.SnapshotCoordinator;
import org.tradinggate.backend.matching.snapshot.writer.SnapshotWriteQueue;

@Log4j2
@Component
@RequiredArgsConstructor
public class SnapshotShutdownManager {

    private final SnapshotWriteQueue snapshotWriteQueue;
    private final SnapshotCoordinator snapshotCoordinator;
    private final AssignedPartitionTracker partitionTracker;
    private final PartitionOffsetTracker offsetTracker;

    @Value("${tradinggate.matching.orders-in-topic:orders.in}")
    private String ordersInTopic;

    @Value("${tradinggate.snapshot.shutdown.drain-timeout-ms:3000}")
    private long drainTimeoutMs;

    @PreDestroy
    public void onShutdown() {
        log.info("[SNAPSHOT] shutdown begin");

        snapshotWriteQueue.stopAccepting();

        try {
            for (TopicPartition tp : partitionTracker.snapshot()) {
                if (!ordersInTopic.equals(tp.topic())) continue;

                long lastOffset = offsetTracker.getLastProcessedOffset(tp.topic(), tp.partition());
                if (lastOffset < 0) {
                    log.info("[SNAPSHOT] shutdown snapshot skip (unknown lastOffset) topic={}, partition={}",
                            tp.topic(), tp.partition());
                    continue;
                }

                snapshotCoordinator.forceSnapshot(
                        tp.topic(),
                        tp.partition(),
                        lastOffset,
                        System.currentTimeMillis()
                );
            }

            boolean drained = snapshotWriteQueue.awaitDrained(drainTimeoutMs);
            if (!drained) {
                log.warn("[SNAPSHOT] shutdown drain timeout (best-effort) timeoutMs={}", drainTimeoutMs);
            }

        } catch (Exception e) {
            log.warn("[SNAPSHOT] shutdown forceSnapshot failed err={}", e.toString(), e);
        } finally {
            snapshotWriteQueue.close();
            log.info("[SNAPSHOT] shutdown end");
        }
    }
}
