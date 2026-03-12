package org.tradinggate.backend.matching.snapshot.dto;

import org.tradinggate.backend.matching.snapshot.model.OrderBookSnapshot;
import org.tradinggate.backend.matching.snapshot.model.PartitionSnapshot;
import org.tradinggate.backend.matching.snapshot.model.e.ChecksumAlgorithm;
import org.tradinggate.backend.matching.snapshot.model.e.CompressionType;
import org.tradinggate.backend.matching.snapshot.model.e.SnapshotTriggerReason;

public record SnapshotWriteRequest(
        String topic,
        int partition,
        long lastProcessedOffset,
        long createdAtMillis,
        SnapshotTriggerReason reason,
        CompressionType compression,
        ChecksumAlgorithm checksumAlg,
        PartitionSnapshot snapshot
) {}
