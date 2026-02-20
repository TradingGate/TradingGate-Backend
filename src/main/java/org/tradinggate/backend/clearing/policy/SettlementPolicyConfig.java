package org.tradinggate.backend.clearing.policy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SettlementPolicyProperties.class)
public class SettlementPolicyConfig {
}
