package org.tradinggate.backend.global.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for API Profile
 * - Trading API Layer용 Producer 전용 설정
 * - Consumer 및 Matching 관련 설정 없음
 */
@Configuration
@Profile("api")  // API 프로필 전용
@RequiredArgsConstructor
public class KafkaApiConfig {

  // ==========================
  // Producer Factory (API용 - orders.in 토픽에 발행만)
  // ==========================
  @Bean
  public ProducerFactory<String, String> producerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
      @Value("${spring.kafka.producer.properties.max.block.ms:3000}") int maxBlockMs,
      @Value("${spring.kafka.producer.request-timeout-ms:3000}") int requestTimeoutMs,
      @Value("${spring.kafka.producer.delivery-timeout-ms:5000}") int deliveryTimeoutMs) {

    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    // Producer 안정성 설정
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, 3);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

    // 타임아웃 설정
    // API는 Kafka 장애 시 요청을 오래 붙잡지 않도록 fail-fast 쪽으로 둔다.
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs);
    props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
    props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);

    return new DefaultKafkaProducerFactory<>(props);
  }

  // ==========================
  // Kafka Template (API용 - orders.in 발행 전용)
  // ==========================
  @Bean
  public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
    return new KafkaTemplate<>(pf);
  }
}
