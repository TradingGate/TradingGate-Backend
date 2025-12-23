package org.tradinggate.backend.matching.snapshot.dto;

public record SnapshotPayload(
        byte[] gzippedJson,
        String sha256Hex
) {}
