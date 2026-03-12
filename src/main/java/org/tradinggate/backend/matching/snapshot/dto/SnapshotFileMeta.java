package org.tradinggate.backend.matching.snapshot.dto;

import java.nio.file.Path;

public record SnapshotFileMeta(
        Path path,
        long offset,
        long createdAtMillis,
        String snapshotId,
        int partition
) {}
