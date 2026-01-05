package org.tradinggate.backend.matching.engine.kafka;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Log4j2
@Component
@RequiredArgsConstructor
public class TopicPartitionCountProvider implements PartitionCountProvider{

    private final AdminClient adminClient;

    @Value("${tradinggate.kafka.admin.request-timeout-ms:3000}")
    private int requestTimeoutMs;

    @Value("${tradinggate.kafka.admin.cache-ttl-ms:30000}")
    private long cacheTtlMs;

    @Value("${tradinggate.kafka.admin.fallback-partitions:12}")
    private int fallbackPartitions;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public int partitionCount(String topic) {
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic must not be blank");

        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(topic);
        if (cached != null && now < cached.expiresAtMillis) {
            return cached.partitionCount;
        }

        try {
            int count = fetchPartitionCountFromBroker(topic);
            cache.put(topic, new CacheEntry(count, now + cacheTtlMs));
            return count;
        } catch (Exception e) {
            if (cached != null) {
                log.warn("[KAFKA] partitionCount fetch failed, use stale cache topic={}, cached={}, err={}",
                        topic, cached.partitionCount, e.toString());
                return cached.partitionCount;
            }

            log.warn("[KAFKA] partitionCount fetch failed, use fallback topic={}, fallback={}, err={}",
                    topic, fallbackPartitions, e.toString());

            if (fallbackPartitions <= 0) {
                throw new IllegalStateException("Failed to fetch partitionCount and fallbackPartitions <= 0", e);
            }
            return fallbackPartitions;
        }
    }

    private int fetchPartitionCountFromBroker(String topic) throws Exception {
        DescribeTopicsResult result = adminClient.describeTopics(java.util.List.of(topic));
        Map<String, TopicDescription> descMap =
                result.allTopicNames().get(requestTimeoutMs, TimeUnit.MILLISECONDS);

        TopicDescription desc = descMap.get(topic);
        if (desc == null) {
            throw new IllegalStateException("TopicDescription missing for topic=" + topic);
        }
        return desc.partitions().size();
    }

    private record CacheEntry(int partitionCount, long expiresAtMillis) {}
}
