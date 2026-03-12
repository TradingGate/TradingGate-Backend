package org.tradinggate.backend.matching.engine.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.tradinggate.backend.matching.snapshot.SnapshotCoordinator;
import org.tradinggate.backend.matching.snapshot.dto.PartitionRecoveryResult;
import org.tradinggate.backend.matching.snapshot.restore.PartitionStateService;
import org.tradinggate.backend.matching.snapshot.shutdown.AssignedPartitionTracker;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


/**
 * - Kafka 리밸런싱 시점에 "파티션 상태"를 복구/정리하는 엔트리 포인트.
 * - ASSIGN: 스냅샷 기반 복구 후, consumer.seek()로 재처리 시작 offset을 맞춘다.
 * - REVOKE/LOST: 파티션을 더 이상 소유하지 않으므로, 해당 파티션 상태(오더북/정책/오프셋)를 정리한다.
 */
@Log4j2
@RequiredArgsConstructor
@Profile("worker")
public class SnapshotRecoveryOnAssign implements ConsumerAwareRebalanceListener {

    private final PartitionStateService partitionStateService;
    private final AssignedPartitionTracker tracker;
    private final String ordersInTopic;

    /**
     * - epoch는 "파티션 단위" 리밸런싱 경쟁조건(race) 방지용 토큰
     * - assign 도중 revoke/lost가 발생할 수 있어, 오래된 작업이 뒤늦게 apply 되는 것을 방어.
     */
    private final AtomicLong epochSeq = new AtomicLong(0);
    private final ConcurrentHashMap<TopicPartition, Long> epochByPartition = new ConcurrentHashMap<>();

    /**
     * 파티션이 할당되면:
     * 1) 복구 수행 (스냅샷 로드 → 오더북 restore)
     * 2) 복구 성공 시 consumer.seek() 적용 (재처리 시작 위치를 스냅샷 offset+1로 고정)
     *
     * @param consumer   현재 리스너가 사용하는 consumer
     * @param partitions 이번에 할당된 파티션 목록
     * @sideEffects tracker에 assigned 업데이트, orderbook registry에 복구된 상태 적재, consumer seek 수행
     */
    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        tracker.onAssigned(partitions);

        for (TopicPartition tp : partitions) {
            if (!isOrdersTopic(tp)) {
                log.debug("[SNAPSHOT] skip recovery for non-orders topic topic={}, partition={}", tp.topic(), tp.partition());
                continue;
            }

            // tp별 epoch 발급: 이 epoch보다 최신 epoch가 생기면, 이번 복구 작업은 폐기
            long epoch = epochSeq.incrementAndGet();
            epochByPartition.put(tp, epoch);

            try {
                // 복구 도중 리밸런싱이 다시 발생하면 apply 되지 않도록 isStillCurrent를 전달
                PartitionRecoveryResult rr = partitionStateService.recoverOnPartitionAssigned(tp.topic(), tp.partition(), ()-> isCurrentEpoch(tp, epoch));

                // 복구가 끝났더라도 epoch가 바뀌었다면 seek/apply를 금지한다.
                if (!isCurrentEpoch(tp, epoch)) {
                    log.info("[SNAPSHOT] skip seek (epoch changed) topic={}, partition={}, epoch={}", tp.topic(), tp.partition(), epoch);
                    continue;
                }

                if (rr.recovered()) {
                    // 스냅샷 offset까지는 "이미 반영된 상태"로 간주하고 offset+1부터 재처리를 시작
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

            // revoke/lost가 오면 해당 tp의 epoch를 폐기하여 assign 방어
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
