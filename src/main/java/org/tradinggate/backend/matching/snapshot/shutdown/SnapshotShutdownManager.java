package org.tradinggate.backend.matching.snapshot.shutdown;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.matching.snapshot.SnapshotCoordinator;
import org.tradinggate.backend.matching.snapshot.writer.SnapshotWriteQueue;

/**
 * - 워커 프로세스 종료 시점에 "현재 할당된 파티션"의 마지막 처리 offset 기준으로 스냅샷을 강제로 1회 남긴다.
 *
 * [정합성 의도]
 * - 리밸런싱/재기동 이후 복구 시, 전체 리플레이 비용을 줄이고 "마지막 처리 지점"부터 재개할 수 있게 한다.
 * - 단, 스냅샷 자체는 best-effort이며 스냅샷 실패가 곧바로 시스템 정합성 파괴로 이어지지 않도록 설계한다.
 *
 * [전제]
 * - PartitionOffsetTracker는 "ack 이후" markProcessed 되므로, 여기서 쓰는 lastOffset은 커밋된 지점과 정합성이 맞는다.
 *   (실패했는데 ack 된 상태의 offset을 찍어버리는 상황을 피하기 위함)
 */
@Log4j2
@Component
@RequiredArgsConstructor
@Profile("worker")
public class SnapshotShutdownManager {

    private final SnapshotWriteQueue snapshotWriteQueue;
    private final SnapshotCoordinator snapshotCoordinator;
    private final AssignedPartitionTracker partitionTracker;
    private final PartitionOffsetTracker offsetTracker;

    @Value("${tradinggate.matching.orders-in-topic:orders.in}")
    private String ordersInTopic;

    @Value("${tradinggate.snapshot.shutdown.drain-timeout-ms:3000}")
    private long drainTimeoutMs;

    /**
     * @sideEffects
     * - snapshot write 큐에 FORCE 요청을 적재하고, 드레인 대기 후 큐를 close 한다.
     *
     * [중요 규칙]
     * - shutdown 시점에는 신규 스냅샷 요청을 더 이상 받지 않도록 stopAccepting()을 먼저 호출한다.
     *   (shutdown 중 enqueue 경쟁으로 인해 drain이 끝나지 않는 상황 방지)
     *
     * [예외 정책]
     * - 종료 훅은 best-effort: 실패하더라도 프로세스 종료를 막지 않는다.
     */
    @PreDestroy
    public void onShutdown() {
        log.info("[SNAPSHOT] shutdown begin");

        // shutdown 동안 신규 스냅샷 요청 유입을 막아 drain 조건을 안정화.
        snapshotWriteQueue.stopAccepting();

        try {
            // 현재 할당된 파티션만 대상.
            for (TopicPartition tp : partitionTracker.snapshot()) {
                if (!ordersInTopic.equals(tp.topic())) continue;

                long lastOffset = offsetTracker.getLastProcessedOffset(tp.topic(), tp.partition());
                if (lastOffset < 0) {
                    log.info("[SNAPSHOT] shutdown snapshot skip (unknown lastOffset) topic={}, partition={}",
                            tp.topic(), tp.partition());
                    continue;
                }

                // shutdown에서는 트리거 정책을 무시하고 강제 발행.
                snapshotCoordinator.forceSnapshot(
                        tp.topic(),
                        tp.partition(),
                        lastOffset,
                        System.currentTimeMillis()
                );
            }

            // 큐가 남아 있으면 파일이 중간에 잘릴 수 있으므로 가능한 범위에서 drain을 기다린다.
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
