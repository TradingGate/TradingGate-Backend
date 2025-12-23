package org.tradinggate.backend.matching.snapshot.dto;

public record PartitionKey(String topic, int partition) {}
