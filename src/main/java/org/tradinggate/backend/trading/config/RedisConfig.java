package org.tradinggate.backend.trading.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * [A-1] Trading API - Redis 설정
 *
 * 역할:
 * - Redis 연결 설정
 * - RedisTemplate Bean 생성
 *
 * TODO:
 * [ ] Redis 연결 정보:
 *     - host: ${redis.host}
 *     - port: ${redis.port}
 *     - password: ${redis.password}
 *
 * [ ] StringRedisTemplate Bean
 *     - 멱등성 체크용 (IdempotencyService)
 *     - 리스크 차단 상태 조회용 (RiskCheckService)
 *
 * [ ] RedisTemplate<String, Object> Bean (선택)
 *
 * [ ] @Profile("api")
 */
@Configuration
@Profile("api")
public class RedisConfig {

  // TODO: RedisConnectionFactory Bean

  // TODO: RedisTemplate Bean
}
