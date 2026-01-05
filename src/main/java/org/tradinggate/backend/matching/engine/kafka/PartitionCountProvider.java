package org.tradinggate.backend.matching.engine.kafka;

@FunctionalInterface
public interface PartitionCountProvider {
    int partitionCount(String topic);
}
