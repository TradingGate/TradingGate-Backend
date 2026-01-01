package org.tradinggate.backend.trading.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

/**
 * [A-1] Trading API - Profile 전용 설정
 *
 * 역할:
 * - profile=api 전용 설정
 * - Kafka Consumer 비활성화 확인
 */
@Slf4j
@Configuration
@Profile("api")
public class ApiProfileConfig {

  /**
   * API 프로필에서는 Kafka Consumer가 동작하면 안 됩니다.
   * 만약 실수로 등록된 리스너가 있다면 강제로 중지하고 경고 로그를 남깁니다.
   */
  @Bean
  public ApplicationRunner kafkaListenerCheck(@Autowired(required = false) KafkaListenerEndpointRegistry registry) {
    return args -> {
      if (registry != null && !registry.getListenerContainerIds().isEmpty()) {
        log.warn("⚠[API Profile Violation] Kafka Listeners detected: {}", registry.getListenerContainerIds());
        log.warn(" Force stopping all Kafka Listeners (API Layer must use PRODUCER only).");
        registry.stop();
      } else {
        log.info("✅ [API Profile Valid] No active Kafka Consumers detected.");
      }
    };
  }
}
