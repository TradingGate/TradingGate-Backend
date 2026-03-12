package org.tradinggate.backend.matching.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.tradinggate.backend.global.config.SnapshotProperties;
import org.tradinggate.backend.matching.engine.kafka.PartitionCountProvider;
import org.tradinggate.backend.matching.engine.kafka.SnapshotRecoveryOnAssign;
import org.tradinggate.backend.matching.engine.model.OrderBookRegistry;
import org.tradinggate.backend.matching.engine.util.MatchingProperties;
import org.tradinggate.backend.matching.snapshot.SnapshotCoordinator;
import org.tradinggate.backend.matching.snapshot.io.AtomicFileWriter;
import org.tradinggate.backend.matching.snapshot.io.LocalSnapshotFileStore;
import org.tradinggate.backend.matching.snapshot.io.SnapshotPathResolver;
import org.tradinggate.backend.matching.snapshot.restore.PartitionStateService;
import org.tradinggate.backend.matching.snapshot.restore.SnapshotLoader;
import org.tradinggate.backend.matching.snapshot.retention.SnapshotRetentionManager;
import org.tradinggate.backend.matching.snapshot.shutdown.AssignedPartitionTracker;
import org.tradinggate.backend.matching.snapshot.shutdown.PartitionOffsetTracker;
import org.tradinggate.backend.matching.snapshot.util.SnapshotAssembler;
import org.tradinggate.backend.matching.snapshot.util.SnapshotFileNameParser;
import org.tradinggate.backend.matching.snapshot.util.SnapshotRestorer;
import org.tradinggate.backend.matching.snapshot.writer.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Profile("worker")
@Configuration
@EnableConfigurationProperties({SnapshotProperties.class, MatchingProperties.class})
@RequiredArgsConstructor
public class SnapshotConfig {

    private final MatchingProperties matchingProperties;

    //kafka 관련 추가설정
    @Bean
    public AdminClient kafkaAdminClient(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${tradinggate.kafka.admin.request-timeout-ms:3000}") int requestTimeoutMs
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        return AdminClient.create(props);
    }

    @Bean(destroyMethod = "close")
    public SnapshotWriteQueue snapshotWriteQueue(
            SnapshotProperties props,
            ObjectMapper objectMapper
    ) {
        SnapshotPathResolver resolver = new SnapshotPathResolver(Path.of(props.getBaseDir()));
        AtomicFileWriter atomicFileWriter = new AtomicFileWriter();
        LocalSnapshotFileStore fileStore = new LocalSnapshotFileStore(resolver, atomicFileWriter);
        SnapshotFileNameParser snapshotFileNameParser = new SnapshotFileNameParser();
        SnapshotRetentionManager manager = new SnapshotRetentionManager(snapshotFileNameParser, resolver, 20);

        SnapshotPayloadCodec codec = new JacksonSnapshotPayloadCodec(objectMapper);

        SnapshotWriteWorker worker = new SnapshotWriteWorker(codec, fileStore, manager);
        return new SnapshotWriteQueue(props.getQueueCapacity(), worker);
    }

    @Bean
    public SnapshotAssembler snapshotAssembler() {
        return new SnapshotAssembler("1");
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
            SnapshotCoordinator snapshotCoordinator,
            PartitionOffsetTracker tracker
    ){
        SnapshotPathResolver pathResolver = new SnapshotPathResolver(matchingProperties.getBaseDir());
        SnapshotLoader snapshotLoader = new SnapshotLoader(pathResolver, objectMapper, matchingProperties.getFallbackCount());
        SnapshotRestorer snapshotRestorer = new SnapshotRestorer();

        return new PartitionStateService(snapshotLoader, snapshotRestorer, registry, partitionCountProvider, snapshotCoordinator, tracker);
    }
}
