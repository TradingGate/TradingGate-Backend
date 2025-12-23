package org.tradinggate.backend.matching.snapshot.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.matching.snapshot.model.e.ChecksumAlgorithm;
import org.tradinggate.backend.matching.snapshot.model.e.CompressionType;
import org.tradinggate.backend.matching.snapshot.model.e.SnapshotTriggerReason;

import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartitionSnapshot {

    private final String snapshotId;
    private final String engineVersion;

    private final String topic;
    private final int partition;

    private final long lastProcessedOffset;
    private final long createdAtMillis;

    private final SnapshotTriggerReason reason;
    private final CompressionType compression;
    private final ChecksumAlgorithm checksumAlg;

    private final int symbolCount;
    private final long totalOrderCount;

    private final List<OrderBookSnapshot> orderBooks;

    @JsonCreator
    public static PartitionSnapshot create(
            @JsonProperty("snapshotId") String snapshotId,
            @JsonProperty("engineVersion") String engineVersion,
            @JsonProperty("topic") String topic,
            @JsonProperty("partition") int partition,
            @JsonProperty("lastProcessedOffset") long lastProcessedOffset,
            @JsonProperty("createdAtMillis") long createdAtMillis,
            @JsonProperty("reason") SnapshotTriggerReason reason,
            @JsonProperty("compression") CompressionType compression,
            @JsonProperty("checksumAlg") ChecksumAlgorithm checksumAlg,
            @JsonProperty("orderBooks") List<OrderBookSnapshot> orderBooks
    ) {
        List<OrderBookSnapshot> safe = (orderBooks == null) ? List.of() : List.copyOf(orderBooks);

        long totalOrders = 0L;
        for (OrderBookSnapshot ob : safe) {
            totalOrders += ob.getOrderCount();
        }

        return PartitionSnapshot.builder()
                .snapshotId(snapshotId)
                .engineVersion(engineVersion)
                .topic(topic)
                .partition(partition)
                .lastProcessedOffset(lastProcessedOffset)
                .createdAtMillis(createdAtMillis)
                .reason(reason)
                .compression(compression)
                .checksumAlg(checksumAlg)
                .symbolCount(safe.size())
                .totalOrderCount(totalOrders)
                .orderBooks(safe)
                .build();
    }
}
