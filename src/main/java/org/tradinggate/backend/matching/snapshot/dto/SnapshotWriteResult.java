package org.tradinggate.backend.matching.snapshot.dto;

import java.nio.file.Path;

public record SnapshotWriteResult(
        String topic,
        int partition,
        Path snapshotPath,
        Path checksumPath
) {}
