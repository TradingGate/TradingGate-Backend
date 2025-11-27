package org.tradinggate.backend.matching.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "tradinggate.matching")
public class MatchingProperties {

    private String ordersInTopic;
    private String tradesExecutedTopic;
    private String ordersUpdatedTopic;
    private String riskCommandsTopic;
    private String partitioningKey;
}
