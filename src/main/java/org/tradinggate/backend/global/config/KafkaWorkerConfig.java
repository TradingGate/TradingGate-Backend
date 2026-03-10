package org.tradinggate.backend.global.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.tradinggate.backend.matching.engine.kafka.SnapshotRecoveryOnAssign;
import org.tradinggate.backend.matching.engine.util.MatchingProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for Worker Profile
 * - Matching Worker용 Consumer/Producer 설정
 * - Snapshot Recovery 및 Rebalance Listener 포함
 */
@Configuration
@Profile("worker")  // Worker 프로필 전용
@EnableKafka
@EnableRetry
@EnableConfigurationProperties(MatchingProperties.class)
public class KafkaWorkerConfig {

  @Value("${spring.kafka.consumer.group-id}")
  private String groupId;

  // ==========================
  // Consumer Factory
  // ==========================
  @Bean
  public ConsumerFactory<String, String> consumerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    return new DefaultKafkaConsumerFactory<>(props);
  }

  // ==========================
  // Kafka Listener Factory (Worker용 - Rebalance Listener 포함)
  // ==========================
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory,
      SnapshotRecoveryOnAssign rebalanceListener) {

    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    factory.getContainerProperties().setConsumerRebalanceListener(rebalanceListener);

    // Error Handler 설정
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(new FixedBackOff(300L, 5L));
    errorHandler.setAckAfterHandle(false);
    errorHandler.addNotRetryableExceptions(
        JsonProcessingException.class,
        IllegalArgumentException.class
    );
    factory.setCommonErrorHandler(errorHandler);

    return factory;
  }

  // ==========================
  // Producer Factory (Worker용)
  // ==========================
  @Bean
  public ProducerFactory<String, String> producerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, 3);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

    return new DefaultKafkaProducerFactory<>(props);
  }

  // ==========================
  // Kafka Template (Producer)
  // ==========================
  @Bean
  public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
    return new KafkaTemplate<>(pf);
  }
}
