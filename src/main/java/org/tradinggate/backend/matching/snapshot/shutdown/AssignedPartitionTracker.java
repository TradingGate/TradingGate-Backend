package org.tradinggate.backend.matching.snapshot.shutdown;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * - 현재 워커 인스턴스에 "할당(assigned)된 파티션 목록"을 추적한다.
 * - shutdown hook에서 "내가 들고 있던 파티션"을 기준으로
 *   스냅샷 flush/drain/정리 로직을 수행하기 위한 기반 데이터로 쓴다.
 *
 * [주의]
 * - Kafka 리밸런싱 이벤트는 중복/순서 변화가 있을 수 있으므로(Set 기반) 멱등하게 처리한다.
 * - snapshot()은 방어적 복사본을 반환하여 외부에서 상태를 오염시키지 못하게 한다.
 */
@Component
@Profile("worker")
public class AssignedPartitionTracker {

    private final Set<TopicPartition> assigned = ConcurrentHashMap.newKeySet();

    /**
     * @param partitions 새로 할당된 파티션들
     * @sideEffects 내부 assigned set에 파티션이 추가된다.
     *
     * [규칙]
     * - assign 콜백은 중복 호출될 수 있으므로 addAll 기반으로 멱등 처리한다.
     */
    public void onAssigned(Collection<TopicPartition> partitions) {
        assigned.addAll(partitions);
    }

    /**
     * @param partitions 더 이상 소유하지 않는 파티션들
     * @sideEffects 내부 assigned set에서 파티션이 제거된다.
     *
     * [규칙]
     * - revoke/lost 모두 "소유권 종료"이므로 동일하게 removeAll로 처리한다.
     */
    public void onUnassigned(Collection<TopicPartition> partitions) {
        assigned.removeAll(partitions);
    }


    /**
     * @return 현재 시점의 assigned 파티션 스냅샷(불변 복사본)
     * @sideEffects 없음
     */
    public Set<TopicPartition> snapshot() {
        return Set.copyOf(assigned);
    }
}
