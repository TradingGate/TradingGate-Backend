package org.tradinggate.backend.matching.snapshot.shutdown;

import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AssignedPartitionTracker {

    private final Set<TopicPartition> assigned = ConcurrentHashMap.newKeySet();

    public void onAssigned(Collection<TopicPartition> partitions) {
        assigned.addAll(partitions);
    }

    public void onUnassigned(Collection<TopicPartition> partitions) {
        assigned.removeAll(partitions);
    }

    public Set<TopicPartition> snapshot() {
        return Set.copyOf(assigned);
    }
}
