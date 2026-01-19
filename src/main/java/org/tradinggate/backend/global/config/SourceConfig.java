package org.tradinggate.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tradinggate.backend.trading.domain.entity.SourceType;

@Configuration
public class SourceConfig {

  @Value("${tradinggate.role:api}")
  private String role;

  @Bean
  public SourceType currentSourceType() {
    try {
      return SourceType.valueOf(role.toUpperCase());
    } catch (IllegalArgumentException e) {
      return SourceType.API;
    }
  }
}
