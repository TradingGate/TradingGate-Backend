package org.tradinggate.backend.trading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * [A-1] Trading API - Redis 설정
 * 역할:
 * - Redis 연결 설정
 * - RedisTemplate Bean 생성
 * TODO:
 * [ ] Redis 연결 정보:
 *     - host: ${redis.host}
 *     - port: ${redis.port}
 *     - password: ${redis.password}
 * [ ] StringRedisTemplate Bean
 *     - 멱등성 체크용 (IdempotencyService)
 *     - 리스크 차단 상태 조회용 (RiskCheckService)
 * [✅️] RedisTemplate<String, Object> Bean
 * [✅️] @Profile("api")
 */
@Configuration
@Profile("api")
public class RedisConfig {

  // TODO: RedisConnectionFactory Bean


  @Bean
  public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
    StringRedisTemplate template = new StringRedisTemplate();
    template.setConnectionFactory(connectionFactory);
    return template;
  }
}
