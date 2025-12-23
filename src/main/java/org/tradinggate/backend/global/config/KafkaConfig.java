package org.tradinggate.backend.global.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.servlet.View;
import org.tradinggate.backend.matching.engine.kafka.PartitionCountProvider;
import org.tradinggate.backend.matching.engine.kafka.SnapshotRecoveryOnAssign;
import org.tradinggate.backend.matching.engine.model.OrderBookRegistry;
import org.tradinggate.backend.matching.engine.util.MatchingProperties;
import org.tradinggate.backend.matching.snapshot.SnapshotCoordinator;
import org.tradinggate.backend.matching.snapshot.io.SnapshotPathResolver;
import org.tradinggate.backend.matching.snapshot.restore.PartitionStateService;
import org.tradinggate.backend.matching.snapshot.restore.SnapshotLoader;
import org.tradinggate.backend.matching.snapshot.shutdown.AssignedPartitionTracker;
import org.tradinggate.backend.matching.snapshot.util.SnapshotRestorer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@EnableRetry
@EnableConfigurationProperties(MatchingProperties.class)
@RequiredArgsConstructor
public class KafkaConfig {

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    private final MatchingProperties matchingProperties;
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
    // Kafka Listener Factory
    // ==========================
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, SnapshotRecoveryOnAssign rebalanceListener, View error) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setConsumerRebalanceListener(rebalanceListener);
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
    // Producer Factory
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

    @Bean
    public SnapshotRecoveryOnAssign snapshotRecoveryOnAssign(
            PartitionStateService partitionStateService,
            AssignedPartitionTracker tracker,
            @Value("${tradinggate.matching.orders-in-topic}") String topic
    ) {
        return new SnapshotRecoveryOnAssign(partitionStateService, tracker, topic);
    }

    @Bean
    public PartitionStateService partitionStateService(
            ObjectMapper objectMapper,
            OrderBookRegistry registry,
            PartitionCountProvider partitionCountProvider,
            SnapshotCoordinator snapshotCoordinator
    ){
        SnapshotPathResolver pathResolver = new SnapshotPathResolver(matchingProperties.getBaseDir());
        SnapshotLoader snapshotLoader = new SnapshotLoader(pathResolver, objectMapper, matchingProperties.getFallbackCount());
        SnapshotRestorer snapshotRestorer = new SnapshotRestorer();

        return new PartitionStateService(snapshotLoader, snapshotRestorer, registry, partitionCountProvider, snapshotCoordinator);
    }
}
