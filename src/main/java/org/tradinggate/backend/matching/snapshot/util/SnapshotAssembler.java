package org.tradinggate.backend.matching.snapshot.util;

import org.tradinggate.backend.matching.engine.model.OrderBook;
import org.tradinggate.backend.matching.engine.model.PriceLevel;
import org.tradinggate.backend.matching.snapshot.model.OrderBookSnapshot;
import org.tradinggate.backend.matching.snapshot.model.PartitionSnapshot;
import org.tradinggate.backend.matching.snapshot.model.PriceLevelSnapshot;
import org.tradinggate.backend.matching.snapshot.model.e.ChecksumAlgorithm;
import org.tradinggate.backend.matching.snapshot.model.e.CompressionType;
import org.tradinggate.backend.matching.snapshot.model.e.SnapshotTriggerReason;

import java.util.*;
import java.util.stream.Collectors;

public class SnapshotAssembler {

    private final String engineVersion;

    public SnapshotAssembler(String engineVersion) {
        this.engineVersion = Objects.requireNonNull(engineVersion, "engineVersion");
    }

    public PartitionSnapshot toPartitionSnapshot(
            String snapshotId,
            String topic,
            int partition,
            long lastProcessedOffset,
            long createdAtMillis,
            SnapshotTriggerReason reason,
            CompressionType compression,
            ChecksumAlgorithm checksumAlg,
            Map<String, OrderBook> orderBooksInPartition
    ) {
        if (snapshotId == null || snapshotId.isBlank()) throw new IllegalArgumentException("snapshotId");
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic");

        List<OrderBookSnapshot> books = orderBooksInPartition.values().stream()
                .filter(Objects::nonNull)
                .map(this::toOrderBookSnapshot)
                .sorted(Comparator.comparing(OrderBookSnapshot::getSymbol))
                .collect(Collectors.toList());

        return PartitionSnapshot.create(
                snapshotId,
                engineVersion,
                topic,
                partition,
                lastProcessedOffset,
                createdAtMillis,
                reason,
                compression,
                checksumAlg,
                books
        );
    }

    public OrderBookSnapshot toOrderBookSnapshot(OrderBook orderBook) {
        List<PriceLevelSnapshot> bids = toLevelSnapshots(orderBook.getBidPriceLevels());
        List<PriceLevelSnapshot> asks = toLevelSnapshots(orderBook.getAskPriceLevels());

        return OrderBookSnapshot.create(
                orderBook.getSymbol(),
                orderBook.getNextOrderId(),
                orderBook.getNextMatchId(),
                bids,
                asks
        );
    }

    private List<PriceLevelSnapshot> toLevelSnapshots(NavigableMap<Long, PriceLevel> levels) {
        if (levels == null || levels.isEmpty()) return List.of();

        List<PriceLevelSnapshot> snapshots = new ArrayList<>(levels.size());
        for (Map.Entry<Long, PriceLevel> e : levels.entrySet()) {
            PriceLevel level = e.getValue();
            if (level == null) continue;
            snapshots.add(PriceLevelSnapshot.fromDomain(level));
        }
        return snapshots;
    }
}
