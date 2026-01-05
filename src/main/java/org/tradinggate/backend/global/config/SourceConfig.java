package org.tradinggate.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.tradinggate.backend.trading.domain.entity.SourceType;

@Configuration
public class SourceConfig {

  @Bean
  @Profile("api")
  public SourceType apiSource() {
    return SourceType.API;
  }

  @Bean
  @Profile("engine")
  public SourceType engineSource() {
    return SourceType.ENGINE;
  }

  @Bean
  @Profile("system")
  public SourceType systemSource() {
    return SourceType.SYSTEM;
  }

  @Bean
  @Profile("risk")
  public SourceType riskSource() {
    return SourceType.RISK;
  }

  @Bean
  @Profile("admin")
  public SourceType adminSource() {
    return SourceType.ADMIN;
  }
}
