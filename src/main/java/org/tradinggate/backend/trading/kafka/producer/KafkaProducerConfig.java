package org.tradinggate.backend.trading.kafka.producer;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * [A-1] Trading API - Kafka Producer 설정
 *
 * 역할:
 * - Kafka Producer Bean 생성
 * - 직렬화 설정
 *
 * TODO:
 * [ ] Producer 설정 확인:
 *     - bootstrap.servers: ${kafka.bootstrap-servers}
 *     - key.serializer: StringSerializer
 *     - value.serializer: JsonSerializer
 *     - acks: all (안전성 보장)
 *     - retries: 3
 *     - enable.idempotence: true (멱등성 보장)
 *     - max.in.flight.requests.per.connection: 5
 *
 * [ ] @Profile("api") 어노테이션 확인
 *     - API 프로필에서만 활성화
 *
 * [ ] KafkaTemplate<String, Object> Bean 생성
 *
 * 참고: PDF 2-2 (Kafka publish)
 */
@Configuration
@Profile("api")
public class KafkaProducerConfig {

  // TODO: @Value로 Kafka 설정 주입

  // TODO: ProducerFactory Bean

  // TODO: KafkaTemplate Bean
}
