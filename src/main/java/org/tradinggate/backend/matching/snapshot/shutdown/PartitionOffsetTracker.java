package org.tradinggate.backend.matching.snapshot.shutdown;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * - 파티션별 "마지막 처리 완료(offset)"를 메모리에서 추적한다.
 * - revokedAfterCommit 시점에 "마지막으로 안전하게 처리된 위치" 기준으로
 *   스냅샷(forceSnapshot)을 찍기 위해 사용한다.
 *
 * [주의]
 * - 이 값은 "정확히 한 번(exactly-once)"를 보장하는 근거가 아니라,
 *   워커 프로세스 내부에서 best-effort로 유지되는 상태다.
 * - ack/commit 순서가 바뀌거나 리스너 예외 처리 정책이 달라지면
 *   markProcessed 호출 시점을 재검토해야 한다.
 */
@Component
@Profile("worker")
public class PartitionOffsetTracker {

    private final ConcurrentHashMap<TopicPartition, Long> lastProcessedOffset = new ConcurrentHashMap<>();

    /**
     * @param topic     처리한 레코드의 topic
     * @param partition 처리한 레코드의 partition
     * @param offset    "처리 완료"로 간주하는 offset
     * @sideEffects 내부 맵에 마지막 처리 offset이 갱신된다.
     *
     * [규칙]
     * - 여기서의 offset은 "엔진 처리 + output 발행까지 성공" 이후에만 기록되어야 한다.
     *   그렇지 않으면 revokedAfterCommit에서 잘못된 offset으로 스냅샷을 찍어
     *   재시작 시 정합성 문제가 생길 수 있다.
     */
    public void markProcessed(String topic, int partition, long offset) {
        lastProcessedOffset.put(new TopicPartition(topic, partition), offset);
    }

    /**
     * @return 마지막으로 처리 완료된 offset. 없으면 -1
     * @sideEffects 없음
     *
     * [예외 케이스]
     * - -1은 "아직 처리된 적 없음" 또는 "정리(clear)됨"을 의미한다.
     */
    public long getLastProcessedOffset(String topic, int partition) {
        return lastProcessedOffset.getOrDefault(new TopicPartition(topic, partition), -1L);
    }

    /**
     * @sideEffects 해당 파티션의 offset 상태를 제거한다.
     *
     * [규칙]
     * - 파티션 소유권을 잃었으면(revoke/lost) 반드시 clear 해서
     *   다음 assign에서 stale offset이 섞이지 않게 한다.
     */
    public void clear(String topic, int partition) {
        lastProcessedOffset.remove(new TopicPartition(topic, partition));
    }
}
