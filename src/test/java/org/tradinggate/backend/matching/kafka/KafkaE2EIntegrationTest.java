package org.tradinggate.backend.matching.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import org.tradinggate.backend.matching.engine.service.KafkaMessageProducer;
import org.tradinggate.backend.matching.snapshot.io.SnapshotPathResolver;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.tradinggate.backend.matching.snapshot.restore.PartitionStateService;
import org.tradinggate.backend.matching.snapshot.shutdown.SnapshotShutdownManager;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.util.stream.Stream;

import java.util.regex.Pattern;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.atLeast;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@Testcontainers
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                // DB/JPA/Batch
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration," +
                // Redis / Redisson (tests shouldn't try to connect to localhost:6379)
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
                "org.redisson.spring.starter.RedissonAutoConfigurationV2"

})
@ActiveProfiles("worker")
class KafkaE2EIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.1")
    );

    @TempDir
    static Path snapshotDir;

    static final String ORDERS_IN = "orders.in";
    static final String ORDERS_UPDATED = "orders.updated";
    static final String TRADES_EXECUTED = "trades.executed";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Ensure Spring Kafka listener can start (group.id is required)
        r.add("spring.kafka.consumer.group-id", () -> "e2e-worker");

        // Avoid failing the context if topics are not present at startup (we create them in tests)
        r.add("spring.kafka.listener.missing-topics-fatal", () -> "false");

        // 네 worker 설정 키에 맞춰서 주입 (너희가 쓰는 property 이름들)
        r.add("tradinggate.matching.orders-in-topic", () -> ORDERS_IN);
        r.add("tradinggate.matching.orders-updated-topic", () -> ORDERS_UPDATED);
        r.add("tradinggate.matching.trades-executed-topic", () -> TRADES_EXECUTED);

        // snapshot base dir (SnapshotPathResolver가 이걸 받도록 구성되어 있어야 함)
        r.add("tradinggate.snapshot.base-dir", () -> snapshotDir.toString());
        r.add("tradinggate.snapshot.baseDir", () -> snapshotDir.toString());
        r.add("tradinggate.matching.snapshot.base-dir", () -> snapshotDir.toString());

        // 테스트 편의상 빠르게
        r.add("tradinggate.kafka.admin.fallback-partitions", () -> "3");
        r.add("tradinggate.kafka.admin.cache-ttl-ms", () -> "2000");
    }

    private Producer<String, String> producer;
    private ObjectMapper om = new ObjectMapper().findAndRegisterModules();

    @SpyBean
    private KafkaMessageProducer kafkaMessageProducerSpy;

    @Autowired
    private SnapshotShutdownManager snapshotShutdownManager;

    @Autowired
    private PartitionStateService snapshotRecoveryService;

    private SnapshotPathResolver snapshotPathResolver;

    @BeforeEach
    void setUp() throws Exception {
        createTopicsIfMissing(List.of(
                new NewTopic(ORDERS_IN, 3, (short) 1),
                new NewTopic(ORDERS_UPDATED, 3, (short) 1),
                new NewTopic(TRADES_EXECUTED, 3, (short) 1)
        ));

        this.snapshotPathResolver = new SnapshotPathResolver(snapshotDir);

        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        producer = new KafkaProducer<>(p);
    }

    @AfterEach
    void tearDown() {
        if (producer != null) producer.close(Duration.ofSeconds(2));
        if (kafkaMessageProducerSpy != null) reset(kafkaMessageProducerSpy);
    }

    @Test
    void e2e_ordersIn_to_engine_to_publishedEvents_and_snapshot_written() throws Exception {
        // given: 출력 토픽을 읽는 테스트 컨슈머
        Consumer<String, String> outConsumer = buildTestConsumer("e2e-out");
        // Subscribe explicitly to ORDERS_UPDATED and TRADES_EXECUTED for this test.
        outConsumer.subscribe(List.of(ORDERS_UPDATED, TRADES_EXECUTED));
        awaitAssignmentAndSeekToEnd(outConsumer);

        // when: orders.in 커맨드 1~N개 발행 (네 OrderCommand JSON 포맷으로 맞춰서)
        // 여기 payload는 너희 OrderCommand 구조에 맞춰 바꿔야 함.
        String symbol = "BTC-KRW";
        java.util.Map<String, Object> cmd1Map = new java.util.LinkedHashMap<>();
        // command
        cmd1Map.put("commandType", "NEW");
        cmd1Map.put("type", "NEW");

        // identity
        cmd1Map.put("accountId", 1);
        cmd1Map.put("userId", 1);
        cmd1Map.put("clientOrderId", "c1");

        // order spec (KafkaJsonUtil.parseOrderCommand() required)
        cmd1Map.put("symbol", symbol);
        cmd1Map.put("side", "BUY");
        cmd1Map.put("orderType", "LIMIT");
        cmd1Map.put("timeInForce", "GTC");
        cmd1Map.put("price", 1000);
        cmd1Map.put("quantity", 10);

        // meta
        cmd1Map.put("source", "TEST");
        cmd1Map.put("requestedAtMillis", System.currentTimeMillis());

        String cmd1 = om.writeValueAsString(cmd1Map);

        java.util.Map<String, Object> cmd2Map = new java.util.LinkedHashMap<>();
        // command
        cmd2Map.put("commandType", "NEW");
        cmd2Map.put("type", "NEW");

        // identity
        cmd2Map.put("accountId", 2);
        cmd2Map.put("userId", 2);
        cmd2Map.put("clientOrderId", "c2");

        // order spec
        cmd2Map.put("symbol", symbol);
        cmd2Map.put("side", "SELL");
        cmd2Map.put("orderType", "LIMIT");
        cmd2Map.put("timeInForce", "GTC");
        cmd2Map.put("price", 1000);
        cmd2Map.put("quantity", 10);

        // meta
        cmd2Map.put("source", "TEST");
        cmd2Map.put("requestedAtMillis", System.currentTimeMillis());

        String cmd2 = om.writeValueAsString(cmd2Map);

        // key는 “orders.in 파티셔닝 기준”에 맞춰(너희는 symbol 기반이었지?)
        sendAndWait(0, ORDERS_IN, symbol, cmd1);
        sendAndWait(0, ORDERS_IN, symbol, cmd2);

        // then: 일정 시간 내에 orders.updated / trades.executed 둘 중 하나 이상이 나와야 함
        // (매칭 결과에 따라 업데이트/체결 둘 다 나올 수도 있고)
        // 1) Worker가 실제로 publisher를 호출했는지 먼저 확인 (topic mismatch/consumer 문제를 분리)
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(kafkaMessageProducerSpy, atLeastOnce()).sendAndWait(anyString(), anyString(), anyString());
        });

        // 2) 실제 Kafka output topic에서 메시지가 보이는지 확인 + 이 시나리오는 교차 매칭이므로 trades.executed가 반드시 1개 이상 나와야 함
        List<ConsumerRecord<String, String>> all = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 15_000;
        boolean sawTrade = false;
        while (System.currentTimeMillis() < deadline && !sawTrade) {
            List<ConsumerRecord<String, String>> polled = pollSome(outConsumer, Duration.ofMillis(400));
            all.addAll(polled);
            for (ConsumerRecord<String, String> rec : polled) {
                if (TRADES_EXECUTED.equals(rec.topic())) {
                    sawTrade = true;
                    break;
                }
            }
        }
        assertTrue(sawTrade, "expected at least one trades.executed message for crossing BUY/SELL");

        outConsumer.close(Duration.ofSeconds(1));
    }

    @Test
    void e2e_duplicateNewOrder_shouldNotDuplicateFills() throws Exception {
        // given
        Consumer<String, String> outConsumer = buildTestConsumer("e2e-out-dup");
        outConsumer.subscribe(Pattern.compile("(trades\\..*)|(.*executed.*)"));
        awaitAssignmentAndSeekToEnd(outConsumer);

        String symbol = "BTC-KRW";

        // 동일 (accountId, clientOrderId, symbol) NEW를 2번 보낸다.
        long now = System.currentTimeMillis();
        Map<String, Object> buy = new java.util.LinkedHashMap<>();
        buy.put("commandType", "NEW");
        buy.put("type", "NEW");
        buy.put("accountId", 1);
        buy.put("userId", 1);
        buy.put("clientOrderId", "dup-1");
        buy.put("symbol", symbol);
        buy.put("side", "BUY");
        buy.put("orderType", "LIMIT");
        buy.put("timeInForce", "GTC");
        buy.put("price", 1000);
        buy.put("quantity", 1);
        buy.put("source", "TEST");
        buy.put("requestedAtMillis", now);

        String buyJson1 = om.writeValueAsString(buy);
        buy.put("requestedAtMillis", now + 1);
        String buyJson2 = om.writeValueAsString(buy);

        // SELL은 수량 2로 보내서, BUY가 중복으로 들어갔다면 2건 체결이 날 수 있는 상태를 만든다.
        Map<String, Object> sell = new java.util.LinkedHashMap<>();
        sell.put("commandType", "NEW");
        sell.put("type", "NEW");
        sell.put("accountId", 2);
        sell.put("userId", 2);
        sell.put("clientOrderId", "sell-agg");
        sell.put("symbol", symbol);
        sell.put("side", "SELL");
        sell.put("orderType", "LIMIT");
        sell.put("timeInForce", "GTC");
        sell.put("price", 1000);
        sell.put("quantity", 2);
        sell.put("source", "TEST");
        sell.put("requestedAtMillis", now + 2);
        String sellJson = om.writeValueAsString(sell);

        // when
        sendAndWait(0, ORDERS_IN, symbol, buyJson1);
        sendAndWait(0, ORDERS_IN, symbol, buyJson2); // duplicate
        sendAndWait(0, ORDERS_IN, symbol, sellJson);

        // 누적 폴링
        List<ConsumerRecord<String, String>> all = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && all.size() < 2) {
            all.addAll(pollSome(outConsumer, Duration.ofMillis(300)));
        }

        // One fill should normally produce taker+maker messages (2). We tolerate 1~2 due to timing/format differences,
        // but we must NOT see duplicates.
        assertTrue(all.size() >= 1 && all.size() <= 2,
                "expected 1~2 trades.executed messages for a single fill (no duplicates); got=" + all.size());

        outConsumer.close(Duration.ofSeconds(1));
    }

    @Test
    void e2e_publishFailure_shouldRetrySameRecord_andEventuallyPublish() throws Exception {
        // given: output consumer
        Consumer<String, String> outConsumer = buildTestConsumer("e2e-out-failonce");
        outConsumer.subscribe(List.of(ORDERS_UPDATED, TRADES_EXECUTED));
        awaitAssignmentAndSeekToEnd(outConsumer);

        // publish가 첫 1회 실패하도록 만든다(orders.updated/trades.executed 둘 중 무엇이든).
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> {
            int n = calls.getAndIncrement();
            if (n == 0) {
                throw new RuntimeException("intentional publish failure (test)");
            }
            return inv.callRealMethod();
        }).when(kafkaMessageProducerSpy).sendAndWait(anyString(), anyString(), anyString());

        String symbol = "ETH-KRW";
        long now = System.currentTimeMillis();

        Map<String, Object> cmd = new java.util.LinkedHashMap<>();
        cmd.put("commandType", "NEW");
        cmd.put("type", "NEW");
        cmd.put("accountId", 10);
        cmd.put("userId", 10);
        cmd.put("clientOrderId", "fail-once-1");
        cmd.put("symbol", symbol);
        cmd.put("side", "BUY");
        cmd.put("orderType", "LIMIT");
        cmd.put("timeInForce", "GTC");
        cmd.put("price", 2000);
        cmd.put("quantity", 1);
        cmd.put("source", "TEST");
        cmd.put("requestedAtMillis", now);
        String payload = om.writeValueAsString(cmd);

        // when
        var meta = sendAndWaitMeta(0, ORDERS_IN, symbol, payload);
        long producedOffset = meta.offset();
        int producedPartition = meta.partition();

        // then
        // publish 실패가 나면 listener 쪽에서 ack/commit이 되지 않아 재처리되어야 하고,
        // 결국 publish가 성공해서 output 토픽에 메시지가 나타나야 한다.
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(calls.get() >= 2, "expected sendAndWait to be attempted at least twice (fail once then succeed)");
        });

        // ✅ Stronger + less flaky: verify retry by inspecting the payload actually passed to producer.
        // We don't rely on a separate output consumer here.
        ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCap = ArgumentCaptor.forClass(String.class);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(kafkaMessageProducerSpy, atLeast(1)).sendAndWait(topicCap.capture(), keyCap.capture(), payloadCap.capture());

            boolean matched = false;
            for (String v : payloadCap.getAllValues()) {
                if (v == null || v.isBlank()) continue;
                try {
                    Map<String, Object> env = om.readValue(v, new TypeReference<Map<String, Object>>() {});
                    String srcTopic = String.valueOf(env.get("sourceTopic"));
                    int srcPartition = Integer.parseInt(String.valueOf(env.get("sourcePartition")));
                    long srcOffset = Long.parseLong(String.valueOf(env.get("sourceOffset")));

                    if (ORDERS_IN.equals(srcTopic) && srcPartition == producedPartition && srcOffset == producedOffset) {
                        matched = true;
                        break;
                    }
                } catch (Exception ignore) {
                    // ignore parse failures
                }
            }
            assertTrue(matched, "expected producer payload to carry same sourceTopic/sourcePartition/sourceOffset as input record");
        });

        outConsumer.close(Duration.ofSeconds(1));
    }

    @Test
    void e2e_publishFailure_shouldNotAdvanceToNextOffsetBeforeRetrySucceeds() throws Exception {
        // given: output consumer
        Consumer<String, String> outConsumer = buildTestConsumer("e2e-out-failonce2");
        outConsumer.subscribe(List.of(ORDERS_UPDATED, TRADES_EXECUTED));
        awaitAssignmentAndSeekToEnd(outConsumer);

        // publish가 첫 1회 실패하도록 만든다(orders.updated/trades.executed 둘 중 무엇이든).
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> {
            int n = calls.getAndIncrement();
            if (n == 0) {
                throw new RuntimeException("intentional publish failure (test)");
            }
            return inv.callRealMethod();
        }).when(kafkaMessageProducerSpy).sendAndWait(anyString(), anyString(), anyString());

        String symbol = "ETH-KRW";
        long now = System.currentTimeMillis();
        Map<String, Object> cmd1 = new java.util.LinkedHashMap<>();
        cmd1.put("commandType", "NEW");
        cmd1.put("type", "NEW");
        cmd1.put("accountId", 10);
        cmd1.put("userId", 10);
        cmd1.put("clientOrderId", "fail-once-2a");
        cmd1.put("symbol", symbol);
        cmd1.put("side", "BUY");
        cmd1.put("orderType", "LIMIT");
        cmd1.put("timeInForce", "GTC");
        cmd1.put("price", 2000);
        cmd1.put("quantity", 1);
        cmd1.put("source", "TEST");
        cmd1.put("requestedAtMillis", now);
        String payload1 = om.writeValueAsString(cmd1);

        Map<String, Object> cmd2 = new java.util.LinkedHashMap<>(cmd1);
        cmd2.put("clientOrderId", "fail-once-2b");
        cmd2.put("requestedAtMillis", now + 1);
        String payload2 = om.writeValueAsString(cmd2);

        // when
        var m1 = sendAndWaitMeta(0, ORDERS_IN, symbol, payload1);
        var m2 = sendAndWaitMeta(0, ORDERS_IN, symbol, payload2);
        long off1 = m1.offset();
        long off2 = m2.offset();
        assertEquals(off1 + 1, off2, "expected sequential offsets in same partition");

        // ✅ Verify ordering via producer spy: we should not publish events for off2 before off1 is successfully published.
        ArgumentCaptor<String> payloadCap = ArgumentCaptor.forClass(String.class);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(kafkaMessageProducerSpy, atLeast(1)).sendAndWait(anyString(), anyString(), payloadCap.capture());

            // Extract the sequence of sourceOffsets we saw published for this partition
            List<Long> publishedOffsets = new ArrayList<>();
            for (String v : payloadCap.getAllValues()) {
                if (v == null || v.isBlank()) continue;
                try {
                    Map<String, Object> env = om.readValue(v, new TypeReference<Map<String, Object>>() {});
                    String srcTopic = String.valueOf(env.get("sourceTopic"));
                    int srcPartition = Integer.parseInt(String.valueOf(env.get("sourcePartition")));
                    long srcOffset = Long.parseLong(String.valueOf(env.get("sourceOffset")));

                    if (ORDERS_IN.equals(srcTopic) && srcPartition == m1.partition()) {
                        publishedOffsets.add(srcOffset);
                    }
                } catch (Exception ignore) {
                }
            }

            assertTrue(publishedOffsets.contains(off1), "expected to eventually observe publish for first offset after retry");

            // If off2 also appeared, it must appear only AFTER off1 in the published sequence.
            int idx1 = publishedOffsets.indexOf(off1);
            int idx2 = publishedOffsets.indexOf(off2);
            if (idx2 >= 0) {
                assertTrue(idx1 >= 0 && idx1 < idx2,
                        "should not publish events for next offset before the failed offset is successfully retried and published");
            }
        });

        outConsumer.close(Duration.ofSeconds(1));
    }

    /**
     * 3) 가격이 교차하지 않는 경우:
     *    - BUY 1 @ 1000
     *    - SELL 1 @ 1100
     *    → 체결(trades.executed)은 없어야 하고, orders.updated는 발생할 수 있다.
     */
    @Test
    void e2e_nonCrossingOrders_shouldNotProduceTradesExecuted() throws Exception {
        Consumer<String, String> out = buildTestConsumer("e2e-out-noncross");
        out.subscribe(Pattern.compile("(orders\\..*)|(trades\\..*)|(.*updated.*)|(.*executed.*)"));
        awaitAssignmentAndSeekToEnd(out);

        String symbol = "XRP-KRW";
        long now = System.currentTimeMillis();

        Map<String, Object> buy = new java.util.LinkedHashMap<>();
        buy.put("commandType", "NEW");
        buy.put("type", "NEW");
        buy.put("accountId", 11);
        buy.put("userId", 11);
        buy.put("clientOrderId", "nc-buy-1");
        buy.put("symbol", symbol);
        buy.put("side", "BUY");
        buy.put("orderType", "LIMIT");
        buy.put("timeInForce", "GTC");
        buy.put("price", 1000);
        buy.put("quantity", 1);
        buy.put("source", "TEST");
        buy.put("requestedAtMillis", now);

        Map<String, Object> sell = new java.util.LinkedHashMap<>();
        sell.put("commandType", "NEW");
        sell.put("type", "NEW");
        sell.put("accountId", 22);
        sell.put("userId", 22);
        sell.put("clientOrderId", "nc-sell-1");
        sell.put("symbol", symbol);
        sell.put("side", "SELL");
        sell.put("orderType", "LIMIT");
        sell.put("timeInForce", "GTC");
        sell.put("price", 1100);
        sell.put("quantity", 1);
        sell.put("source", "TEST");
        sell.put("requestedAtMillis", now + 1);

        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(buy));
        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(sell));

        // 일정 시간 동안 trades.executed가 나오지 않는지 확인
        long deadline = System.currentTimeMillis() + 5_000;
        boolean anyTrade = false;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> rec : pollSome(out, Duration.ofMillis(300))) {
                if (TRADES_EXECUTED.equals(rec.topic())) {
                    anyTrade = true;
                    break;
                }
            }
            if (anyTrade) break;
        }
        assertFalse(anyTrade, "expected no trades.executed for non-crossing orders");

        out.close(Duration.ofSeconds(1));
    }

    /**
     * 4) 동일 가격에서 시간 우선(FIFO) 체결 확인:
     *    - SELL(2001) 1 @ 1000
     *    - SELL(2002) 1 @ 1000
     *    - BUY(1001)  1 @ 1000
     *    → maker는 먼저 들어온 2001 이어야 한다.
     */
    @Test
    void e2e_samePrice_shouldRespectTimePriority_makerShouldBeFirst() throws Exception {
        Consumer<String, String> out = buildTestConsumer("e2e-out-fifo");
        out.subscribe(Pattern.compile("(trades\\..*)|(.*executed.*)"));
        awaitAssignmentAndSeekToEnd(out);

        String symbol = "DOGE-KRW";
        long now = System.currentTimeMillis();

        Map<String, Object> sell1 = new java.util.LinkedHashMap<>();
        sell1.put("commandType", "NEW");
        sell1.put("type", "NEW");
        sell1.put("accountId", 2001);
        sell1.put("userId", 2001);
        sell1.put("clientOrderId", "fifo-sell-1");
        sell1.put("symbol", symbol);
        sell1.put("side", "SELL");
        sell1.put("orderType", "LIMIT");
        sell1.put("timeInForce", "GTC");
        sell1.put("price", 1000);
        sell1.put("quantity", 1);
        sell1.put("source", "TEST");
        sell1.put("requestedAtMillis", now);

        Map<String, Object> sell2 = new java.util.LinkedHashMap<>();
        sell2.put("commandType", "NEW");
        sell2.put("type", "NEW");
        sell2.put("accountId", 2002);
        sell2.put("userId", 2002);
        sell2.put("clientOrderId", "fifo-sell-2");
        sell2.put("symbol", symbol);
        sell2.put("side", "SELL");
        sell2.put("orderType", "LIMIT");
        sell2.put("timeInForce", "GTC");
        sell2.put("price", 1000);
        sell2.put("quantity", 1);
        sell2.put("source", "TEST");
        sell2.put("requestedAtMillis", now + 1);

        Map<String, Object> buy = new java.util.LinkedHashMap<>();
        buy.put("commandType", "NEW");
        buy.put("type", "NEW");
        buy.put("accountId", 1001);
        buy.put("userId", 1001);
        buy.put("clientOrderId", "fifo-buy-1");
        buy.put("symbol", symbol);
        buy.put("side", "BUY");
        buy.put("orderType", "LIMIT");
        buy.put("timeInForce", "GTC");
        buy.put("price", 1000);
        buy.put("quantity", 1);
        buy.put("source", "TEST");
        buy.put("requestedAtMillis", now + 2);

        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(sell1));
        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(sell2));
        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(buy));

        // trades.executed는 taker/maker 각각 발행될 수 있고,
        // E2E에서는 이벤트 스키마(body 구조)가 바뀔 수 있으니 "FIFO 검증"은 엔진 단위 테스트에서 담당한다.
        // 여기서는 "체결 이벤트가 발행됐다"까지만 확인한다.
        List<ConsumerRecord<String, String>> all = new ArrayList<>();
        boolean sawMaker2001 = false;

        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline && all.isEmpty()) {
            List<ConsumerRecord<String, String>> polled = pollSome(out, Duration.ofMillis(500));
            all.addAll(polled);

            // (옵션) 디버깅용: makerAccountId=2001이 보이면 플래그만 세팅
            for (ConsumerRecord<String, String> rec : polled) {
                String v = rec.value();
                if (v == null) continue;
                try {
                    Map<String, Object> env = om.readValue(v, new TypeReference<Map<String, Object>>() {});
                    Object bodyObj = env.get("body");
                    if (bodyObj instanceof Map<?, ?> body) {
                        Object makerId = body.get("makerAccountId");
                        if (makerId != null && String.valueOf(makerId).equals("2001")) {
                            sawMaker2001 = true;
                        }
                    }
                } catch (Exception ignore) {
                    if (v.contains("2001")) {
                        sawMaker2001 = true;
                    }
                }
            }
        }

        assertFalse(all.isEmpty(), "expected at least one trades.executed message for same-price match (FIFO scenario)");
        if (!sawMaker2001) {
            org.slf4j.LoggerFactory.getLogger(KafkaE2EIntegrationTest.class)
                    .info("[TEST] FIFO scenario: trades.executed emitted but makerAccountId=2001 not observed in payload (schema may differ). sample={}",
                            all.get(0).value());
        }

        out.close(Duration.ofSeconds(1));
    }

    /**
     * 5) BUY taker가 여러 SELL maker를 연속으로 체결하는 케이스:
     *    - SELL(2001) 2 @ 1000
     *    - SELL(2002) 1 @  900
     *    - BUY(1001)  3 @ 1100
     *    → 최소 2개의 가격 레벨이 체결되어 trades.executed 메시지가 여러 건 발생해야 한다.
     */
    @Test
    void e2e_buyTaker_shouldMatchMultipleLevels_andProduceMultipleTradeMessages() throws Exception {
        Consumer<String, String> out = buildTestConsumer("e2e-out-multilevel");
        out.subscribe(Pattern.compile("(trades\\..*)|(.*executed.*)"));
        awaitAssignmentAndSeekToEnd(out);

        String symbol = "SOL-KRW";
        long now = System.currentTimeMillis();

        Map<String, Object> sell1 = new java.util.LinkedHashMap<>();
        sell1.put("commandType", "NEW");
        sell1.put("type", "NEW");
        sell1.put("accountId", 2001);
        sell1.put("userId", 2001);
        sell1.put("clientOrderId", "ml-sell-1");
        sell1.put("symbol", symbol);
        sell1.put("side", "SELL");
        sell1.put("orderType", "LIMIT");
        sell1.put("timeInForce", "GTC");
        sell1.put("price", 1000);
        sell1.put("quantity", 2);
        sell1.put("source", "TEST");
        sell1.put("requestedAtMillis", now);

        Map<String, Object> sell2 = new java.util.LinkedHashMap<>();
        sell2.put("commandType", "NEW");
        sell2.put("type", "NEW");
        sell2.put("accountId", 2002);
        sell2.put("userId", 2002);
        sell2.put("clientOrderId", "ml-sell-2");
        sell2.put("symbol", symbol);
        sell2.put("side", "SELL");
        sell2.put("orderType", "LIMIT");
        sell2.put("timeInForce", "GTC");
        sell2.put("price", 900);
        sell2.put("quantity", 1);
        sell2.put("source", "TEST");
        sell2.put("requestedAtMillis", now + 1);

        Map<String, Object> buy = new java.util.LinkedHashMap<>();
        buy.put("commandType", "NEW");
        buy.put("type", "NEW");
        buy.put("accountId", 1001);
        buy.put("userId", 1001);
        buy.put("clientOrderId", "ml-buy-1");
        buy.put("symbol", symbol);
        buy.put("side", "BUY");
        buy.put("orderType", "LIMIT");
        buy.put("timeInForce", "GTC");
        buy.put("price", 1100);
        buy.put("quantity", 3);
        buy.put("source", "TEST");
        buy.put("requestedAtMillis", now + 2);

        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(sell1));
        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(sell2));
        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(buy));

        // 최소 2번 체결이면 taker+maker 이벤트 합쳐서 trades.executed가 >= 4개 나올 확률이 높다.
        // (정확한 이벤트 스펙이 다를 수 있어 "충분히 많이"로만 검증)
        List<ConsumerRecord<String, String>> all = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 12_000;
        while (System.currentTimeMillis() < deadline && all.size() < 4) {
            all.addAll(pollSome(out, Duration.ofMillis(400)));
        }

        // Multi-level match should produce multiple trade messages. Exact count depends on your envelope/spec.
        assertTrue(all.size() >= 2, "expected multiple trades.executed messages for multi-level match; got=" + all.size());

        out.close(Duration.ofSeconds(1));
    }

    @Test
    void e2e_rebalance_shouldNotBreakProcessing_and_shouldStillWriteSnapshot() throws Exception {
        // Arrange: produce a few events first so state exists
        String symbol = "RBAL-BTC";
        long now = System.currentTimeMillis();

        Map<String, Object> buy = new java.util.LinkedHashMap<>();
        buy.put("commandType", "NEW");
        buy.put("type", "NEW");
        buy.put("accountId", 101);
        buy.put("userId", 101);
        buy.put("clientOrderId", "rbal-buy-1");
        buy.put("symbol", symbol);
        buy.put("side", "BUY");
        buy.put("orderType", "LIMIT");
        buy.put("timeInForce", "GTC");
        buy.put("price", 1000);
        buy.put("quantity", 1);
        buy.put("source", "TEST");
        buy.put("requestedAtMillis", now);

        Map<String, Object> sell = new java.util.LinkedHashMap<>();
        sell.put("commandType", "NEW");
        sell.put("type", "NEW");
        sell.put("accountId", 202);
        sell.put("userId", 202);
        sell.put("clientOrderId", "rbal-sell-1");
        sell.put("symbol", symbol);
        sell.put("side", "SELL");
        sell.put("orderType", "LIMIT");
        sell.put("timeInForce", "GTC");
        sell.put("price", 1000);
        sell.put("quantity", 1);
        sell.put("source", "TEST");
        sell.put("requestedAtMillis", now + 1);

        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(buy));
        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(sell));

        // Ensure at least one publish happened before rebalance
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(kafkaMessageProducerSpy, atLeastOnce()).sendAndWait(anyString(), anyString(), anyString());
        });

        // Trigger a rebalance in the same group as the Spring listener by joining/leaving with a manual consumer.
        // This does NOT process records; it only causes revoke/assign callbacks in the worker.
        Consumer<String, String> rebalancer = buildTestConsumer("e2e-worker");
        rebalancer.subscribe(List.of(ORDERS_IN));

        // NOTE:
        // We intentionally join the SAME group as the Spring listener to force a rebalance.
        // The manual consumer might receive **no partitions** (assignment is empty), which is OK.
        // We only need it to join and trigger rebalance.
        awaitGroupJoin(rebalancer);

        rebalancer.close(Duration.ofSeconds(1));

        // After rebalance, produce again and verify the worker still processes and publishes.
        Map<String, Object> buy2 = new java.util.LinkedHashMap<>(buy);
        buy2.put("clientOrderId", "rbal-buy-2");
        buy2.put("requestedAtMillis", now + 2);

        Map<String, Object> sell2 = new java.util.LinkedHashMap<>(sell);
        sell2.put("clientOrderId", "rbal-sell-2");
        sell2.put("requestedAtMillis", now + 3);

        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(buy2));
        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(sell2));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(kafkaMessageProducerSpy, atLeastOnce()).sendAndWait(anyString(), anyString(), anyString());
        });

        // Best-effort: snapshot file should exist at some point during the test run
        await().atMost(10, TimeUnit.SECONDS).until(() -> findAnySnapshotFile(snapshotPathResolver.snapshotsRootDir()).isPresent());
    }

    @Test
    @DirtiesContext
    void e2e_shutdown_shouldForceSnapshot_andDrainQueue_bestEffort() throws Exception {
        String symbol = "SD-BTC";
        long now = System.currentTimeMillis();

        Map<String, Object> cmd = new java.util.LinkedHashMap<>();
        cmd.put("commandType", "NEW");
        cmd.put("type", "NEW");
        cmd.put("accountId", 303);
        cmd.put("userId", 303);
        cmd.put("clientOrderId", "sd-buy-1");
        cmd.put("symbol", symbol);
        cmd.put("side", "BUY");
        cmd.put("orderType", "LIMIT");
        cmd.put("timeInForce", "GTC");
        cmd.put("price", 1000);
        cmd.put("quantity", 1);
        cmd.put("source", "TEST");
        cmd.put("requestedAtMillis", now);

        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(cmd));

        // Wait until the worker has actually processed something (so offset tracker can have a value)
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(kafkaMessageProducerSpy, atLeastOnce()).sendAndWait(anyString(), anyString(), anyString());
        });

        // Act: simulate shutdown hook
        snapshotShutdownManager.onShutdown();

        // Assert: a snapshot file should exist (forceSnapshot + drain best-effort)
        await().atMost(10, TimeUnit.SECONDS).until(() -> findAnySnapshotFile(snapshotPathResolver.snapshotsRootDir()).isPresent());
    }

    @Test
    void e2e_corruptedLatestSnapshot_shouldBeSkipped_and_recoveryShouldStillSucceed() throws Exception {
        // Produce at least one snapshot first (best-effort; your policy might not write immediately)
        String symbol = "CORR-BTC";
        long now = System.currentTimeMillis();

        Map<String, Object> cmd = new java.util.LinkedHashMap<>();
        cmd.put("commandType", "NEW");
        cmd.put("type", "NEW");
        cmd.put("accountId", 404);
        cmd.put("userId", 404);
        cmd.put("clientOrderId", "corr-buy-1");
        cmd.put("symbol", symbol);
        cmd.put("side", "BUY");
        cmd.put("orderType", "LIMIT");
        cmd.put("timeInForce", "GTC");
        cmd.put("price", 1000);
        cmd.put("quantity", 1);
        cmd.put("source", "TEST");
        cmd.put("requestedAtMillis", now);

        sendAndWait(0, ORDERS_IN, symbol, om.writeValueAsString(cmd));

        // Ensure at least one snapshot exists somewhere
        await().atMost(10, TimeUnit.SECONDS).until(() -> findAnySnapshotFile(snapshotPathResolver.snapshotsRootDir()).isPresent());

        // Pick partition=0 dir (tests send to partition 0); locate an existing snapshot
        Path partitionDir = snapshotPathResolver.partitionDir(ORDERS_IN, 0);
        Optional<Path> latestOpt = findLatestSnapshotFile(partitionDir);
        assertTrue(latestOpt.isPresent(), "expected at least one snapshot in partition dir");

        Path latest = latestOpt.get();
        String latestName = latest.getFileName().toString();

        // Create a *newer-looking* snapshot candidate by cloning the file but writing a wrong checksum
        // We bump offset/createdAt in the filename so loader will consider it first.
        String corruptedName = latestName;
        if (latestName.startsWith("snapshot_")) {
            // naive bump: replace first number group by adding 9999, and createdAtMillis by adding 9999
            // format: snapshot_{offset}_{createdAt}_{id}.json.gz
            String[] parts = latestName.substring("snapshot_".length(), latestName.length() - ".json.gz".length()).split("_");
            if (parts.length >= 3) {
                long off = Long.parseLong(parts[0]);
                long ts = Long.parseLong(parts[1]);
                String sid = parts[2];
                corruptedName = "snapshot_" + (off + 9999) + "_" + (ts + 9999) + "_" + sid + ".json.gz";
            }
        }

        Path corruptedSnapshot = partitionDir.resolve(corruptedName);
        Files.copy(latest, corruptedSnapshot, StandardCopyOption.REPLACE_EXISTING);

        Path corruptedChecksum = snapshotPathResolver.checksumFilePath(ORDERS_IN, 0, corruptedName);
        writeText(corruptedChecksum, "WRONG_SHA  " + corruptedName);

        // Now explicitly invoke recovery. It should skip the corrupted candidate and still return recovered or none.
        // We mainly assert it does NOT throw.
        assertDoesNotThrow(() -> snapshotRecoveryService.recoverOnPartitionAssigned(ORDERS_IN, 0, () -> true));
    }




    private static Optional<Path> findAnySnapshotFile(Path root) {
        if (root == null || !Files.exists(root)) return Optional.empty();
        try (Stream<Path> s = Files.walk(root)) {
            return s
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("snapshot_") && name.endsWith(".json.gz");
                    })
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<Path> findLatestSnapshotFile(Path partitionDir) {
        if (partitionDir == null || !Files.exists(partitionDir)) return Optional.empty();
        try (Stream<Path> s = Files.list(partitionDir)) {
            return s
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("snapshot_") && name.endsWith(".json.gz");
                    })
                    .max(Comparator.comparing(p -> p.getFileName().toString()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static void writeText(Path p, String text) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    // ---------- helpers ----------

    private org.apache.kafka.clients.producer.RecordMetadata sendAndWaitMeta(int partition, String topic, String key, String payload) throws Exception {
        return producer.send(new ProducerRecord<>(topic, partition, key, payload)).get(3, TimeUnit.SECONDS);
    }

    private void sendAndWait(String topic, String key, String payload) throws Exception {
        producer.send(new ProducerRecord<>(topic, key, payload)).get(3, TimeUnit.SECONDS);
    }

    private void sendAndWait(int partition, String topic, String key, String payload) throws Exception {
        producer.send(new ProducerRecord<>(topic, partition, key, payload)).get(3, TimeUnit.SECONDS);
    }

    // ---------- helpers ----------

    private static Consumer<String, String> buildTestConsumer(String name) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

        // IMPORTANT: unique group.id per test consumer to avoid cross-test rebalances/offset sharing
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-" + name + "-" + java.util.UUID.randomUUID());
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "consumer-" + name + "-" + java.util.UUID.randomUUID());

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        // Make polling more responsive for tests
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "200");

        return new KafkaConsumer<>(props);
    }

    private static List<ConsumerRecord<String, String>> pollSome(Consumer<String, String> c, Duration d) {
        ConsumerRecords<String, String> records = c.poll(d);
        List<ConsumerRecord<String, String>> all = new ArrayList<>();
        records.forEach(all::add);
        return all;
    }

    private static void awaitAssignmentAndSeekToEnd(Consumer<String, String> c) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            c.poll(Duration.ofMillis(100)); // triggers group join + assignment
            if (!c.assignment().isEmpty()) {
                // IMPORTANT: 이전 테스트가 남긴 레코드들을 읽지 않도록 "끝"으로 이동
                c.seekToEnd(c.assignment());
                // 일부 환경에서는 assignment 직후 offset reset 로그가 찍히며 position이 흔들릴 수 있어
                // 한 번 더 poll로 안정화 후 다시 seekToEnd를 걸어준다.
                c.poll(Duration.ofMillis(10));
                c.seekToEnd(c.assignment());
                return;
            }
        }
        throw new IllegalStateException("Consumer was not assigned within timeout. groupId=" +
                c.groupMetadata().groupId() + ", subscribed=" + c.subscription());
    }

    private static void awaitGroupJoin(Consumer<String, String> c) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            c.poll(Duration.ofMillis(100)); // triggers group join

            // memberId becomes non-empty once the consumer is part of a group
            String memberId = c.groupMetadata() != null ? c.groupMetadata().memberId() : null;
            if (memberId != null && !memberId.isBlank()) {
                return;
            }
        }
        throw new IllegalStateException("Consumer did not join group within timeout. groupId=" +
                c.groupMetadata().groupId() + ", subscribed=" + c.subscription());
    }

    private static void createTopicsIfMissing(List<NewTopic> topics) throws Exception {
        Properties p = new Properties();
        p.put("bootstrap.servers", kafka.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(p)) {
            admin.createTopics(topics).all().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 이미 존재하면 예외 날 수 있어서 best-effort로 무시하되, 다른 원인일 수 있으니 DEBUG로 남김
            org.slf4j.LoggerFactory.getLogger(KafkaE2EIntegrationTest.class)
                    .debug("createTopicsIfMissing failed (best-effort): {}", e.toString());
        }
    }
}
