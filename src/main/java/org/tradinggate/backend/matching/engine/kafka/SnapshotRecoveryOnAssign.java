package org.tradinggate.backend.matching.engine.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.tradinggate.backend.matching.snapshot.SnapshotCoordinator;
import org.tradinggate.backend.matching.snapshot.dto.PartitionRecoveryResult;
import org.tradinggate.backend.matching.snapshot.restore.PartitionStateService;
import org.tradinggate.backend.matching.snapshot.shutdown.AssignedPartitionTracker;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Log4j2
@RequiredArgsConstructor
public class SnapshotRecoveryOnAssign implements ConsumerAwareRebalanceListener {

    private final PartitionStateService partitionStateService;
    private final AssignedPartitionTracker tracker;
    private final String ordersInTopic;

    private final AtomicLong epochSeq = new AtomicLong(0);
    private final ConcurrentHashMap<TopicPartition, Long> epochByPartition = new ConcurrentHashMap<>();

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        tracker.onAssigned(partitions);

        for (TopicPartition tp : partitions) {
            if (!isOrdersTopic(tp)) {
                log.debug("[SNAPSHOT] skip recovery for non-orders topic topic={}, partition={}", tp.topic(), tp.partition());
                continue;
            }

            long epoch = epochSeq.incrementAndGet();
            epochByPartition.put(tp, epoch);

            try {
                PartitionRecoveryResult rr = partitionStateService.recoverOnPartitionAssigned(tp.topic(), tp.partition(), ()-> isCurrentEpoch(tp, epoch));

                if (!isCurrentEpoch(tp, epoch)) {
                    log.info("[SNAPSHOT] skip seek (epoch changed) topic={}, partition={}, epoch={}", tp.topic(), tp.partition(), epoch);
                    continue;
                }

                if (rr.recovered()) {
                    consumer.seek(tp, rr.seekStartOffset());

                    log.info("[SNAPSHOT] seek applied topic={}, partition={}, seekOffset={}, recoveredSymbols={}",
                            tp.topic(), tp.partition(), rr.seekStartOffset(), rr.recoveredSymbols());
                } else {
                    log.info("[SNAPSHOT] no snapshot for topic={}, partition={} (use Kafka default position)", tp.topic(), tp.partition());
                }
            } catch (Exception e) {
                log.warn("[SNAPSHOT] recovery-on-assign failed topic={}, partition={}, err={}", tp.topic(), tp.partition(), e.toString(), e);
            }
        }
    }

    @Override
    public void onPartitionsRevokedAfterCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        cleanupOnUnassign(partitions, "revokedAfterCommit");
        tracker.onUnassigned(partitions);
    }

    @Override
    public void onPartitionsLost(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        cleanupOnUnassign(partitions, "lost");
        tracker.onUnassigned(partitions);
    }

    private void cleanupOnUnassign(Collection<TopicPartition> partitions, String reason) {
        for (TopicPartition tp : partitions) {
            if (!isOrdersTopic(tp)) continue;

            epochByPartition.remove(tp);

            try {
                partitionStateService.cleanupOnPartitionUnassigned(tp.topic(), tp.partition(), reason);
            } catch (Exception e) {
                log.warn("[SNAPSHOT] cleanup failed topic={}, partition={}, reason={}, err={}",
                        tp.topic(), tp.partition(), reason, e.toString(), e);
            }
        }
    }

    private boolean isOrdersTopic(TopicPartition tp) {
        return tp != null && ordersInTopic.equals(tp.topic());
    }

    private boolean isCurrentEpoch(TopicPartition tp, long epoch) {
        Long cur = epochByPartition.get(tp);
        return cur != null && cur == epoch;
    }
}
