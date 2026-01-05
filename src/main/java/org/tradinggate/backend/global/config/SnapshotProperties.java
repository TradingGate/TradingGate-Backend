package org.tradinggate.backend.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "tradinggate.matching.snapshot")
public class SnapshotProperties {
    private String baseDir = "./data";
    private int queueCapacity = 10_000;
}
