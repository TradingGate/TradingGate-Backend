package org.tradinggate.backend.matching.engine.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@Getter
@Setter
@ConfigurationProperties(prefix = "tradinggate.matching")
public class MatchingProperties {

    private String ordersInTopic;
    private String tradesExecutedTopic;
    private String ordersUpdatedTopic;
    private String riskCommandsTopic;
    private String partitioningKey;
    private String ordersInPartitions;
    private Path baseDir;
    private int fallbackCount;
}
