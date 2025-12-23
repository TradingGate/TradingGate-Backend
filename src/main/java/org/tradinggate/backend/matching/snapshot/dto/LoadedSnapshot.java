package org.tradinggate.backend.matching.snapshot.dto;

import org.tradinggate.backend.matching.snapshot.model.PartitionSnapshot;

public record LoadedSnapshot(
        SnapshotFileMeta meta,
        PartitionSnapshot snapshot
) {}
