package org.tradinggate.backend.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxProperties;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {
}
