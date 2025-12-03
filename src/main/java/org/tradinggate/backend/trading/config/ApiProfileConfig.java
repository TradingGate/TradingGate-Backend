package org.tradinggate.backend.trading.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * [A-1] Trading API - Profile 전용 설정
 *
 * 역할:
 * - profile=api 전용 설정
 * - Kafka Consumer 비활성화 확인
 *
 * TODO:
 * [ ] Kafka Consumer 비활성화 확인:
 *     - @KafkaListener는 worker 프로필에서만 활성화
 *     - API Layer는 Producer만 사용
 *
 * [ ] 프로필별 Bean 조건부 로딩
 *
 * 참고: PDF A-1 요구사항 7 (API 프로필에서 Kafka Listener 비활성화)
 */
@Configuration
@Profile("api")
public class ApiProfileConfig {
  //check
  // TODO: API 전용 설정
}
