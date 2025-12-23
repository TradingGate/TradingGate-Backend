package org.tradinggate.backend.matching.snapshot.shutdown;

import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class PartitionOffsetTracker {

    private final ConcurrentHashMap<TopicPartition, Long> lastProcessedOffset = new ConcurrentHashMap<>();

    public void markProcessed(String topic, int partition, long offset) {
        lastProcessedOffset.put(new TopicPartition(topic, partition), offset);
    }

    public long getLastProcessedOffset(String topic, int partition) {
        return lastProcessedOffset.getOrDefault(new TopicPartition(topic, partition), -1L);
    }
}
