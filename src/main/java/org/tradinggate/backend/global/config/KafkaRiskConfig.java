package org.tradinggate.backend.global.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for Risk Service
 *
 * 목적:
 * - trades.executed 소비 (핵심)
 * - orders.updated 소비 (참고)
 * - risk.commands 발행 (선택)
 * - clearing.settlement 발행 (선택)
 *
 * 특징:
 * - Snapshot Recovery 제거 (Risk는 stateless)
 * - Manual Ack (멱등성 보장)
 * - Error Handling (재시도 + 불가능 예외 제외)
 */
@Slf4j
@Configuration
@Profile("risk")  // Risk 프로필 전용
@EnableKafka
@EnableRetry
@RequiredArgsConstructor
public class KafkaRiskConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${spring.kafka.consumer.group-id}")
  private String groupId;

  // ==========================
  // Consumer Factory
  // ==========================

  /**
   * Risk Consumer Factory
   *
   * 설정:
   * - group-id: risk-service-group (application.yml)
   * - auto-offset-reset: earliest (처음부터 소비)
   * - enable-auto-commit: false (수동 커밋)
   */
  @Bean
  public ConsumerFactory<String, String> riskConsumerFactory() {
    log.info("Initializing Risk Consumer Factory: groupId={}, bootstrapServers={}",
        groupId, bootstrapServers);

    Map<String, Object> props = new HashMap<>();

    // 기본 설정
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

    // Deserializer
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    // Offset 관리
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual Ack

    // Performance Tuning
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);          // 한번에 최대 100개
    props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);          // 최소 1KB
    props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);         // 최대 0.5초 대기

    // Session & Heartbeat
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);      // 30초
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);   // 10초

    // Isolation Level (Read Committed - 트랜잭션 완료된 것만)
    props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

    return new DefaultKafkaConsumerFactory<>(props);
  }

  // ==========================
  // Kafka Listener Factory
  // ==========================

  /**
   * Risk Kafka Listener Container Factory
   *
   * 특징:
   * - Manual Ack: 처리 완료 후 명시적 커밋
   * - Error Handler: 재시도 5회 (300ms 간격)
   * - Not Retryable: JsonProcessingException, IllegalArgumentException
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> riskConsumerFactory) {

    log.info("Initializing Kafka Listener Container Factory for Risk Service");

    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(riskConsumerFactory);

    // Manual Ack 설정
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

    // Concurrency (파티션 수에 맞춰 설정 - application.yml)
    // factory.setConcurrency(3); // yml에서 설정 가능

    // Error Handler 설정
    DefaultErrorHandler errorHandler = createErrorHandler();
    factory.setCommonErrorHandler(errorHandler);

    return factory;
  }

  /**
   * Error Handler 생성
   *
   * 전략:
   * 1. 재시도 가능: 일시적 오류 (DB timeout, network 등)
   * 2. 재시도 불가능: 데이터 오류 (JSON 파싱, 잘못된 인자)
   * 3. 최대 5회 재시도 (300ms 간격)
   */
  private DefaultErrorHandler createErrorHandler() {
    // Fixed Backoff: 300ms 간격, 5회 재시도
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(
        new FixedBackOff(300L, 5L)
    );

    // 재시도 후에도 Ack 안 함 (문제 해결될 때까지 블락)
    errorHandler.setAckAfterHandle(false);

    // 재시도 불가능한 예외 (즉시 실패)
    errorHandler.addNotRetryableExceptions(
        JsonProcessingException.class,      // JSON 파싱 오류
        IllegalArgumentException.class,     // 잘못된 인자
        NullPointerException.class          // Null 참조
    );

    log.info("Error Handler configured: maxRetries=5, interval=300ms");

    return errorHandler;
  }

  // ==========================
  // Producer Factory (선택)
  // ==========================

  /**
   * Risk Producer Factory
   *
   * 목적:
   * - risk.commands 발행 (잔고 부족 시 거래 중단)
   * - clearing.settlement 발행 (일일 정산 리포트)
   *
   * 설정:
   * - acks: all (모든 replica 확인)
   * - retries: 3 (재시도)
   * - idempotence: true (중복 방지)
   * - max-in-flight: 1 (순서 보장)
   */
  @Bean
  public ProducerFactory<String, String> riskProducerFactory() {
    log.info("Initializing Risk Producer Factory: bootstrapServers={}", bootstrapServers);

    Map<String, Object> props = new HashMap<>();

    // 기본 설정
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

    // Serializer
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    // Reliability (신뢰성)
    props.put(ProducerConfig.ACKS_CONFIG, "all");                        // 모든 replica 확인
    props.put(ProducerConfig.RETRIES_CONFIG, 3);                         // 3회 재시도
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);           // 멱등성 보장

    // Ordering (순서 보장)
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);  // 순서 보장

    // Performance
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");         // 압축
    props.put(ProducerConfig.LINGER_MS_CONFIG, 10);                      // 10ms 대기 (배치)
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);                  // 16KB 배치

    return new DefaultKafkaProducerFactory<>(props);
  }

  // ==========================
  // Kafka Template
  // ==========================

  /**
   * Risk Kafka Template
   *
   * 사용:
   * - riskCommandProducer.sendBlockAccount(...)
   * - clearingEventProducer.sendDailySettlement(...)
   */
  @Bean
  public KafkaTemplate<String, String> kafkaTemplate(
      ProducerFactory<String, String> riskProducerFactory) {

    log.info("Initializing Kafka Template for Risk Service");

    return new KafkaTemplate<>(riskProducerFactory);
  }
}
