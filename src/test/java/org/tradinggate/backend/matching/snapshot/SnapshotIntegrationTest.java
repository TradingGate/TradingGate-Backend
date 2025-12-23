package org.tradinggate.backend.matching.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tradinggate.backend.matching.engine.kafka.PartitionCountProvider;
import org.tradinggate.backend.matching.engine.kafka.SnapshotRecoveryOnAssign;
import org.tradinggate.backend.matching.engine.kafka.TopicPartitionCountProvider;
import org.tradinggate.backend.matching.engine.model.OrderBook;
import org.tradinggate.backend.matching.engine.model.OrderBookRegistry;
import org.tradinggate.backend.matching.snapshot.dto.PartitionRecoveryResult;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteRequest;
import org.tradinggate.backend.matching.snapshot.io.AtomicFileWriter;
import org.tradinggate.backend.matching.snapshot.io.LocalSnapshotFileStore;
import org.tradinggate.backend.matching.snapshot.io.SnapshotPathResolver;
import org.tradinggate.backend.matching.snapshot.model.OrderBookSnapshot;
import org.tradinggate.backend.matching.snapshot.model.PartitionSnapshot;
import org.tradinggate.backend.matching.snapshot.model.e.ChecksumAlgorithm;
import org.tradinggate.backend.matching.snapshot.model.e.CompressionType;
import org.tradinggate.backend.matching.snapshot.model.e.SnapshotTriggerReason;
import org.tradinggate.backend.matching.snapshot.restore.PartitionStateService;
import org.tradinggate.backend.matching.snapshot.restore.SnapshotLoader;
import org.tradinggate.backend.matching.snapshot.retention.SnapshotRetentionManager;
import org.tradinggate.backend.matching.snapshot.shutdown.AssignedPartitionTracker;
import org.tradinggate.backend.matching.snapshot.util.SnapshotAssembler;
import org.tradinggate.backend.matching.snapshot.util.SnapshotCryptoUtils;
import org.tradinggate.backend.matching.snapshot.util.SnapshotFileNameParser;
import org.tradinggate.backend.matching.snapshot.util.SnapshotRestorer;
import org.tradinggate.backend.matching.snapshot.writer.JacksonSnapshotPayloadCodec;
import org.tradinggate.backend.matching.snapshot.writer.SnapshotPayloadCodec;
import org.tradinggate.backend.matching.snapshot.writer.SnapshotWriteQueue;
import org.tradinggate.backend.matching.snapshot.writer.SnapshotWriteWorker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SnapshotIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void snapshot_write_then_clear_memory_then_recover_should_restore_and_seekOffset() throws Exception {
        // given
        String topic = "orders.in";
        int partitionCount = 12;

        // 심볼에 맞는 파티션으로 테스트를 맞춰야 함 (필터링 때문에)
        String symbol = "BTCUSDT";
        int partition = org.tradinggate.backend.matching.snapshot.util.SnapshotCryptoUtils.resolve(symbol, partitionCount);

        long offset = 555L;
        long now = System.currentTimeMillis();

        ObjectMapper om = new ObjectMapper().findAndRegisterModules();
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);

        PartitionCountProvider countProvider = t -> partitionCount;
        OrderBookRegistry registry = new OrderBookRegistry(countProvider);

        // registry에 book 넣기 (최소 상태)
        OrderBook book = registry.getOrCreate(symbol);
        // TODO: 필요하면 book에 주문/레벨 채워서 스냅샷 용량/구조도 같이 검증 가능

        SnapshotPayloadCodec codec = new JacksonSnapshotPayloadCodec(om);
        LocalSnapshotFileStore store = new LocalSnapshotFileStore(resolver, new AtomicFileWriter());
        SnapshotRetentionManager retention = new SnapshotRetentionManager(new SnapshotFileNameParser(), resolver, 20);
        SnapshotWriteWorker worker = new SnapshotWriteWorker(codec, store, retention);

        SnapshotWriteQueue writeQueue = new SnapshotWriteQueue(1000, worker);

        SnapshotAssembler assembler = new SnapshotAssembler("engine-v1");
        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, assembler, writeQueue);

        SnapshotLoader loader = new SnapshotLoader(resolver, om, 5);
        SnapshotRestorer restorer = new SnapshotRestorer();
        PartitionStateService partitionStateService =
                new PartitionStateService(loader, restorer, registry, countProvider, coordinator);

        // when: force snapshot -> 실제 파일 저장
        coordinator.forceSnapshot(topic, partition, offset, now);

        // 큐 drain 보장 (너희 구현에 awaitDrained가 있으면 그걸 쓰고, 없으면 close가 drain 하도록)
        writeQueue.awaitDrained(3_000);
        writeQueue.stopAccepting();

        // in-memory clear (리밸런싱에서 하던 것과 동일)
        registry.removeAllByPartition(topic, partition);

        assertTrue(registry.find(symbol).isEmpty(), "precondition: registry should be empty after clear");

        // then: recover
        PartitionRecoveryResult rr = partitionStateService.recoverOnPartitionAssigned(topic, partition, () -> true);

        assertTrue(rr.recovered(), "should recover");
        assertEquals(offset + 1, rr.seekStartOffset(), "seekStartOffset should be snapshotOffset+1");

        Optional<OrderBook> restored = registry.find(symbol);
        assertTrue(restored.isPresent(), "book should be restored");
        assertEquals(symbol, restored.get().getSymbol());

        // cleanup
        writeQueue.close();
    }

    @Test
    void multiPartition_multiSymbol_snapshotAndRecover_shouldWork() throws Exception {
        // --- given ---
        ObjectMapper om = new ObjectMapper();
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);

        String topic = "orders.in";
        int partitionCount = 12;

        // 테스트용 파티션 카운트 고정
        PartitionCountProvider countProvider = t -> partitionCount;

        OrderBookRegistry registry = new OrderBookRegistry(countProvider);

        // snapshot writer stack
        SnapshotPayloadCodec codec = new JacksonSnapshotPayloadCodec(om);
        LocalSnapshotFileStore store = new LocalSnapshotFileStore(resolver, new AtomicFileWriter());
        SnapshotRetentionManager retention = new SnapshotRetentionManager(new SnapshotFileNameParser(), resolver, 20);
        SnapshotWriteWorker worker = new SnapshotWriteWorker(codec, store, retention);

        // 큐는 단일 스레드 실행 (close 시 drain)
        SnapshotWriteQueue writeQueue = new SnapshotWriteQueue(1000, worker);

        // PartitionSnapshot assembler/coordinator
        SnapshotAssembler assembler = new SnapshotAssembler("engine-test");
        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, assembler, writeQueue);

        // recovery stack
        SnapshotLoader loader = new SnapshotLoader(resolver, om, 5);
        SnapshotRestorer restorer = new SnapshotRestorer();
        PartitionStateService stateService =
                new PartitionStateService(loader, restorer, registry, countProvider, coordinator);

        // 테스트 대상 파티션 2개
        int p1 = 4;
        int p2 = 7;

        // 각 파티션에 들어갈 심볼 3개씩 “해시로” 찾아서 준비
        List<String> p1Symbols = pickSymbolsForPartition(p1, partitionCount, 3);
        List<String> p2Symbols = pickSymbolsForPartition(p2, partitionCount, 3);

        // registry에 심볼별 orderbook 생성 (비어있는 book이어도 snapshot/restore는 됨)
        for (String s : p1Symbols) registry.getOrCreate(s);
        for (String s : p2Symbols) registry.getOrCreate(s);

        long now = System.currentTimeMillis();

        // 파티션별 스냅샷 생성 (offset은 파티션마다 다르게)
        long offsetP1 = 1000L;
        long offsetP2 = 2000L;

        coordinator.forceSnapshot(topic, p1, offsetP1, now);
        coordinator.forceSnapshot(topic, p2, offsetP2, now);

        // flush (best-effort)
        writeQueue.stopAccepting();
        writeQueue.close();

        // --- when ---
        // 메모리 비우기 (파티션별 제거)
        List<String> removedP1 = registry.removeAllByPartition(topic, p1);
        List<String> removedP2 = registry.removeAllByPartition(topic, p2);

        assertEquals(new HashSet<>(p1Symbols), new HashSet<>(removedP1));
        assertEquals(new HashSet<>(p2Symbols), new HashSet<>(removedP2));

        // 복구 실행
        PartitionRecoveryResult rr1 = stateService.recoverOnPartitionAssigned(topic, p1, () -> true);
        PartitionRecoveryResult rr2 = stateService.recoverOnPartitionAssigned(topic, p2, () -> true);

        // --- then ---
        assertTrue(rr1.recovered(), "p1 should be recovered");
        assertTrue(rr2.recovered(), "p2 should be recovered");

        assertEquals(offsetP1 + 1, rr1.seekStartOffset(), "p1 seekStartOffset should be snapshotOffset+1");
        assertEquals(offsetP2 + 1, rr2.seekStartOffset(), "p2 seekStartOffset should be snapshotOffset+1");

        // registry에 다시 들어왔는지 확인
        for (String s : p1Symbols) {
            Optional<OrderBook> ob = registry.find(s);
            assertTrue(ob.isPresent(), "p1 symbol should exist after recovery: " + s);
            assertEquals(s, ob.get().getSymbol());
        }
        for (String s : p2Symbols) {
            Optional<OrderBook> ob = registry.find(s);
            assertTrue(ob.isPresent(), "p2 symbol should exist after recovery: " + s);
            assertEquals(s, ob.get().getSymbol());
        }

        // 혹시 cross contamination 없는지 (p1 심볼은 p2가 아니어야 함)
        for (String s : p1Symbols) {
            assertEquals(p1, SnapshotCryptoUtils.resolve(s, partitionCount));
        }
        for (String s : p2Symbols) {
            assertEquals(p2, SnapshotCryptoUtils.resolve(s, partitionCount));
        }
    }

    /**
     * partitionCount 기준으로 resolve(symbol) == targetPartition 인 심볼을 n개 뽑는다.
     * 테스트에서는 "가능한" 심볼만 찾으면 되므로 간단한 brute-force로 충분.
     */
    private static List<String> pickSymbolsForPartition(int targetPartition, int partitionCount, int n) {
        List<String> result = new ArrayList<>();
        for (int i = 0; result.size() < n && i < 1_000_000; i++) {
            String sym = "SYM-" + targetPartition + "-" + i;
            if (SnapshotCryptoUtils.resolve(sym, partitionCount) == targetPartition) {
                result.add(sym);
            }
        }
        if (result.size() < n) {
            throw new IllegalStateException("Failed to pick enough symbols for partition=" + targetPartition);
        }
        return result;
    }

    @Test
    void mixedPartitions_oneHasSnapshot_otherHasNone_shouldRecoverOnlyOne() throws Exception {
        ObjectMapper om = new ObjectMapper();
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);

        String topic = "orders.in";
        int partitionCount = 12;
        PartitionCountProvider countProvider = t -> partitionCount;

        OrderBookRegistry registry = new OrderBookRegistry(countProvider);

        SnapshotPayloadCodec codec = new JacksonSnapshotPayloadCodec(om);
        LocalSnapshotFileStore store = new LocalSnapshotFileStore(resolver, new AtomicFileWriter());
        SnapshotRetentionManager retention = new SnapshotRetentionManager(new SnapshotFileNameParser(), resolver, 20);
        SnapshotWriteWorker worker = new SnapshotWriteWorker(codec, store, retention);

        SnapshotWriteQueue writeQueue = new SnapshotWriteQueue(1000, worker);
        SnapshotAssembler assembler = new SnapshotAssembler("engine-test");
        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, assembler, writeQueue);

        SnapshotLoader loader = new SnapshotLoader(resolver, om, 5);
        SnapshotRestorer restorer = new SnapshotRestorer();
        PartitionStateService stateService =
                new PartitionStateService(loader, restorer, registry, countProvider, coordinator);

        int pHas = 4;
        int pNone = 7;

        // pHas에만 심볼 넣고 snapshot 생성
        List<String> pHasSymbols = pickSymbolsForPartition(pHas, partitionCount, 3);
        for (String s : pHasSymbols) registry.getOrCreate(s);

        long now = System.currentTimeMillis();
        long offsetHas = 1234L;

        coordinator.forceSnapshot(topic, pHas, offsetHas, now);

        // flush
        writeQueue.stopAccepting();
        writeQueue.close();

        // memory cleanup
        registry.removeAllByPartition(topic, pHas);
        registry.removeAllByPartition(topic, pNone);

        // when: recover both
        PartitionRecoveryResult rrHas = stateService.recoverOnPartitionAssigned(topic, pHas, () -> true);
        PartitionRecoveryResult rrNone = stateService.recoverOnPartitionAssigned(topic, pNone, () -> true);

        // then
        assertTrue(rrHas.recovered());
        assertEquals(offsetHas + 1, rrHas.seekStartOffset());

        assertFalse(rrNone.recovered());
        assertEquals(-1L, rrNone.seekStartOffset());
        assertEquals(0, rrNone.recoveredSymbols());

        // registry에는 pHasSymbols만 존재해야 함
        for (String s : pHasSymbols) {
            assertTrue(registry.find(s).isPresent(), "should be recovered: " + s);
        }
    }

    @Test
    void recover_shouldSkipBadChecksumAndLoadNextCandidate_thenRecover() throws Exception {
        ObjectMapper om = new ObjectMapper();
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);

        String topic = "orders.in";
        int partitionCount = 12;
        PartitionCountProvider countProvider = t -> partitionCount;

        OrderBookRegistry registry = new OrderBookRegistry(countProvider);

        SnapshotLoader loader = new SnapshotLoader(resolver, om, 5);
        SnapshotRestorer restorer = new SnapshotRestorer();

        // coordinator는 이 테스트에서 직접 파일 만든 뒤 recovery만 보니까, policy/queue 없이도 됨.
        // 하지만 PartitionStateService 생성 시 coordinator 필요하면 "더미 coordinator"를 넣어야 할 수 있음.
        // (네 PartitionStateService 생성자 시그니처에 맞춰서 넣어줘)
        SnapshotPayloadCodec codec = new JacksonSnapshotPayloadCodec(om);
        LocalSnapshotFileStore store = new LocalSnapshotFileStore(resolver, new AtomicFileWriter());
        SnapshotRetentionManager retention = new SnapshotRetentionManager(new SnapshotFileNameParser(), resolver, 20);
        SnapshotWriteWorker worker = new SnapshotWriteWorker(codec, store, retention);
        SnapshotWriteQueue writeQueue = new SnapshotWriteQueue(10, worker);
        SnapshotAssembler assembler = new SnapshotAssembler("engine-test");
        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, assembler, writeQueue);

        PartitionStateService stateService =
                new PartitionStateService(loader, restorer, registry, countProvider, coordinator);

        int partition = 4;

        // 이 partition으로 해시되는 심볼 2개
        List<String> symbols = pickSymbolsForPartition(partition, partitionCount, 2);
        for (String s : symbols) registry.getOrCreate(s);

        long now = System.currentTimeMillis();

        // 정상 스냅샷(older)
        long goodOffset = 100L;
        String goodSid = "sid-good";
        coordinator.forceSnapshot(topic, partition, goodOffset, now);

        // flush해서 good 파일이 실제로 쓰이게 함
        writeQueue.stopAccepting();
        writeQueue.close();

        // 이제 최신(bad) 파일을 "직접" 생성: 더 큰 offset + checksum WRONG
        long badOffset = 200L;
        long badCreatedAt = now + 1;
        String badSid = "sid-bad";

        // 최신 파일명 생성
        String badFileName = resolver.snapshotFileName(badOffset, badCreatedAt, badSid);
        Path badSnapshotPath = resolver.snapshotFilePath(topic, partition, badFileName);
        Path badChecksumPath = resolver.checksumFilePath(topic, partition, badFileName);

        // payload는 정상 gz json (PartitionSnapshot)
        PartitionSnapshot badPs = PartitionSnapshot.create(
                badSid,
                "engine-test",
                topic, partition,
                badOffset, badCreatedAt,
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                symbols.stream().map(s -> OrderBookSnapshot.create(
                        s, 1L, 1L, List.of(), List.of()
                )).toList()
        );

        byte[] badJson = om.writeValueAsBytes(badPs);
        byte[] badGz = SnapshotCryptoUtils.gzip(badJson); // gzip util 없으면 아래 helper 사용

        Files.createDirectories(badSnapshotPath.getParent());
        Files.write(badSnapshotPath, badGz);

        // checksum을 일부러 틀리게 작성
        String wrongSha = "WRONG_SHA";
        Files.writeString(badChecksumPath, wrongSha + "  " + badSnapshotPath.getFileName());

        // memory cleanup
        registry.removeAllByPartition(topic, partition);

        // when: recover -> bad checksum 스킵, good snapshot 로드
        PartitionRecoveryResult rr = stateService.recoverOnPartitionAssigned(topic, partition, () -> true);

        // then: goodOffset 기반 seek
        assertTrue(rr.recovered());
        assertEquals(goodOffset + 1, rr.seekStartOffset());

        for (String s : symbols) {
            assertTrue(registry.find(s).isPresent(), "should be recovered from GOOD snapshot: " + s);
        }
    }

    private static String findSymbolForPartition(int targetPartition, int partitionCount, String prefix) {
        for (int i = 0; i < 100_000; i++) {
            String candidate = prefix + i;
            int p = SnapshotCryptoUtils.resolve(candidate, partitionCount);
            if (p == targetPartition) return candidate;
        }
        throw new IllegalStateException("Failed to find symbol for partition=" + targetPartition);
    }

    private static String findSymbolNotInPartition(int forbiddenPartition, int partitionCount, String prefix) {
        for (int i = 0; i < 100_000; i++) {
            String candidate = prefix + i;
            int p = SnapshotCryptoUtils.resolve(candidate, partitionCount);
            if (p != forbiddenPartition) return candidate;
        }
        throw new IllegalStateException("Failed to find symbol not in partition=" + forbiddenPartition);
    }

    @Test
    void recover_shouldPutOnlySymbolsBelongingToThatPartition() throws Exception {
        ObjectMapper om = new ObjectMapper();
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);

        // partitionCountProvider 고정
        int partitionCount = 12;
        PartitionCountProvider countProvider = t -> partitionCount;

        OrderBookRegistry registry = new OrderBookRegistry(new TopicPartitionCountProviderStub(countProvider));

        SnapshotLoader loader = new SnapshotLoader(resolver, om, 5);
        SnapshotRestorer restorer = new SnapshotRestorer();

        int partition = 4;
        String topic = "orders.in";
        long offset = 555;
        long createdAt = System.currentTimeMillis();
        String snapshotId = "sid-555";

        String inSymbol = findSymbolForPartition(partition, partitionCount, "IN-");
        String outSymbol = findSymbolNotInPartition(partition, partitionCount, "OUT-");

        // OrderBookSnapshot 2개(섞어서)
        OrderBookSnapshot inObs = OrderBookSnapshot.create(
                inSymbol,
                100, 200,
                List.of(),
                List.of()
        );
        OrderBookSnapshot outObs = OrderBookSnapshot.create(
                outSymbol,
                300, 400,
                List.of(),
                List.of()
        );

        PartitionSnapshot ps = PartitionSnapshot.create(
                snapshotId,
                "engine-v1",
                topic, partition,
                offset, createdAt,
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                List.of(inObs, outObs)
        );

        // 파일에 저장
        SnapshotPayloadCodec codec = new JacksonSnapshotPayloadCodec(om);
        LocalSnapshotFileStore store = new LocalSnapshotFileStore(resolver, new AtomicFileWriter());
        SnapshotRetentionManager retention = new SnapshotRetentionManager(new SnapshotFileNameParser(), resolver, 20);
        SnapshotWriteWorker worker = new SnapshotWriteWorker(codec, store, retention);

        worker.write(new SnapshotWriteRequest(
                topic, partition, offset, createdAt,
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                ps
        ));

        // coordinator는 여기서 크게 쓰지 않으니 null로 안 두고 더미 제공
        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, new SnapshotAssembler("engine-v1"), new SnapshotWriteQueue(10, worker));

        PartitionStateService svc = new PartitionStateService(
                loader, restorer, registry,
                t -> partitionCount,
                coordinator
        );

        PartitionRecoveryResult rr = svc.recoverOnPartitionAssigned(topic, partition, () -> true);

        // inSymbol만 registry에 들어가야 함
        assertTrue(rr.recovered());
        assertEquals(offset + 1, rr.seekStartOffset());

        assertTrue(registry.find(inSymbol).isPresent(), "inSymbol must be restored");
        assertTrue(registry.find(outSymbol).isEmpty(), "outSymbol must be filtered out");
    }

    // ===== stubs =====
    static class TopicPartitionCountProviderStub extends TopicPartitionCountProvider {
        private final PartitionCountProvider provider;
        TopicPartitionCountProviderStub(PartitionCountProvider provider) {
            super(null); // AdminClient 안 쓰게끔, 실제 클래스 구조에 맞춰 조정 필요
            this.provider = provider;
        }
        @Override public int partitionCount(String topic) {
            return provider.partitionCount(topic);
        }
    }

    @Test
    void ifPartitionLostDuringRecovery_seekMustBeSkipped() throws Exception {
        // given
        PartitionStateService recoveryService = mock(PartitionStateService.class);
        AssignedPartitionTracker tracker = mock(AssignedPartitionTracker.class);

        String ordersInTopic = "orders.in";

        CountDownLatch enteredRecovery = new CountDownLatch(1);
        CountDownLatch allowRecoveryReturn = new CountDownLatch(1);

        TopicPartition tp = new TopicPartition(ordersInTopic, 1);
        // tracker stubs (explicit, so it doesn't interfere)
        doNothing().when(tracker).onAssigned(anyCollection());
        doNothing().when(tracker).onUnassigned(anyCollection());

        when(recoveryService.recoverOnPartitionAssigned(eq(tp.topic()), eq(tp.partition()), any()))
                .thenAnswer(inv -> {
                    enteredRecovery.countDown();
                    // block until lost cleanup happens
                    allowRecoveryReturn.await(2, TimeUnit.SECONDS);
                    return PartitionRecoveryResult.recovered(
                            1,
                            123L,
                            List.of()
                    );
                });

        SnapshotRecoveryOnAssign listener = new SnapshotRecoveryOnAssign(
                recoveryService,
                tracker,
                ordersInTopic
        );

        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumer = mock(Consumer.class);

        // when: assigned in another thread
        Thread t = new Thread(() -> listener.onPartitionsAssigned(consumer, List.of(tp)));
        t.start();

        assertTrue(enteredRecovery.await(1, TimeUnit.SECONDS), "recovery must start");

        // lost comes in while recovery is running
        listener.onPartitionsLost(consumer, List.of(tp));

        // allow recovery to finish
        allowRecoveryReturn.countDown();
        t.join(2000);

        // then: seek must NOT happen due to epoch invalidation
        verify(consumer, never()).seek(tp, 123L);
        // recovery was invoked for assigned partition
        verify(recoveryService, times(1)).recoverOnPartitionAssigned(eq(tp.topic()), eq(tp.partition()), any());
        // tracker notified about unassignment on lost
        verify(tracker, times(1)).onUnassigned(argThat(c -> c.contains(tp)));
    }

    @Test
    void ifPartitionCountChanged_recoveryShouldResultInNone() throws Exception {
        ObjectMapper om = new ObjectMapper();
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);

        int partition = 4;
        String topic = "orders.in";
        long offset = 777;
        long createdAt = System.currentTimeMillis();
        String snapshotId = "sid-777";

        // snapshot 만들 때는 partitionCount=12를 기준으로 "partition=4" 심볼을 찾음
        int snapshotPartitionCount = 12;
        String symbol = findSymbolForPartition(partition, snapshotPartitionCount, "SYM-");

        OrderBookSnapshot obs = OrderBookSnapshot.create(
                symbol,
                10, 20,
                List.of(), List.of()
        );

        PartitionSnapshot ps = PartitionSnapshot.create(
                snapshotId,
                "engine-v1",
                topic, partition,
                offset, createdAt,
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                List.of(obs)
        );

        // 저장
        SnapshotPayloadCodec codec = new JacksonSnapshotPayloadCodec(om);
        LocalSnapshotFileStore store = new LocalSnapshotFileStore(resolver, new AtomicFileWriter());
        SnapshotRetentionManager retention = new SnapshotRetentionManager(new SnapshotFileNameParser(), resolver, 20);
        SnapshotWriteWorker worker = new SnapshotWriteWorker(codec, store, retention);

        worker.write(new SnapshotWriteRequest(
                topic, partition, offset, createdAt,
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                ps
        ));

        SnapshotLoader loader = new SnapshotLoader(resolver, om, 5);
        SnapshotRestorer restorer = new SnapshotRestorer();

        // ✅ provider가 “복구 시점”에 partitionCount=13을 반환하도록 구성 (변경 상황 가정)
        AtomicInteger call = new AtomicInteger(0);
        PartitionCountProvider changingProvider = t -> (call.incrementAndGet() >= 1 ? 13 : 13);

        OrderBookRegistry registry = new OrderBookRegistry(new TopicPartitionCountProviderStub(changingProvider));

        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, new SnapshotAssembler("engine-v1"), new SnapshotWriteQueue(10, worker));

        PartitionStateService svc = new PartitionStateService(
                loader, restorer, registry,
                changingProvider::partitionCount,
                coordinator
        );

        PartitionRecoveryResult rr = svc.recoverOnPartitionAssigned(topic, partition, () -> true);

        // partitionCount가 바뀌어 symbol이 더 이상 partition=4로 해시 안 되면
        // => recovered=false (or recovered=true but symbolsMatched=0) 정책 중 너가 택한 것으로 assert 맞추기
        // 네 로그 기준: "snapshot exists but no symbols matched -> skip seek calc"
        assertFalse(rr.recovered(), "should skip seek when no symbols matched due to partitionCount change");

        assertTrue(registry.find(symbol).isEmpty(), "registry must not be polluted");
    }

}
