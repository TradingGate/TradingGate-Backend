package org.tradinggate.backend.matching.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tradinggate.backend.matching.engine.kafka.PartitionCountProvider;
import org.tradinggate.backend.matching.engine.kafka.SnapshotRecoveryOnAssign;
import org.tradinggate.backend.matching.engine.model.OrderBookRegistry;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteRequest;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteResult;
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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.tradinggate.backend.matching.snapshot.util.SnapshotCryptoUtils.gzip;

public class RebalanceScenarioIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void rebalance_A_revoked_B_assigned_should_cleanup_and_recover_from_shared_snapshot() throws Exception {
        // ===== given =====
        String topic = "orders.in";
        int partitionCount = 12;
        PartitionCountProvider countProvider = t -> partitionCount;

        // 공유 스냅샷 디렉토리 (PVC 공유처럼)
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();

        // --- A 워커 스택 ---
        OrderBookRegistry registryA = new OrderBookRegistry(countProvider);
        SnapshotWriteQueue queueA = buildWriteQueue(resolver, om);
        SnapshotCoordinator coordinatorA = new SnapshotCoordinator(registryA, new SnapshotAssembler("engine-test"), queueA);
        PartitionStateService stateA = buildStateService(resolver, om, registryA, countProvider, coordinatorA);
        AssignedPartitionTracker trackerA = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listenerA = new SnapshotRecoveryOnAssign(stateA, trackerA, topic);

        // --- B 워커 스택 ---
        OrderBookRegistry registryB = new OrderBookRegistry(countProvider);
        SnapshotWriteQueue queueB = buildWriteQueue(resolver, om);
        SnapshotCoordinator coordinatorB = new SnapshotCoordinator(registryB, new SnapshotAssembler("engine-test"), queueB);
        PartitionStateService stateB = buildStateService(resolver, om, registryB, countProvider, coordinatorB);
        AssignedPartitionTracker trackerB = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listenerB = new SnapshotRecoveryOnAssign(stateB, trackerB, topic);

        // 파티션 2개를 A가 처음 가짐
        TopicPartition p0 = new TopicPartition(topic, 0);
        TopicPartition p1 = new TopicPartition(topic, 1);

        // p0/p1에 맞는 심볼을 준비해서 A registry에 넣고 스냅샷 생성
        String s0 = pickSymbolForPartition(0, partitionCount, "A0-");
        String s1 = pickSymbolForPartition(1, partitionCount, "A1-");

        registryA.getOrCreate(s0);
        registryA.getOrCreate(s1);

        long now = System.currentTimeMillis();
        coordinatorA.forceSnapshot(topic, 0, 100L, now);
        coordinatorA.forceSnapshot(topic, 1, 200L, now);

        // flush
        queueA.stopAccepting();
        queueA.close();

        // A가 assign 받아 recover(seek)까지 수행한다고 가정
        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumerA = mock(Consumer.class);

        listenerA.onPartitionsAssigned(consumerA, List.of(p0, p1));

        // ===== when =====
        // 리밸런싱: A는 p1 revoke, B는 p1 assign
        listenerA.onPartitionsRevokedAfterCommit(consumerA, List.of(p1));

        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumerB = mock(Consumer.class);

        listenerB.onPartitionsAssigned(consumerB, List.of(p1));

        // ===== then =====
        // A: p1 데이터는 cleanup 되었어야 함 (registry에서 제거)
        assertTrue(registryA.find(s1).isEmpty(), "A must cleanup revoked partition's symbol");

        // B: p1 snapshot에서 복구되어야 함
        Optional<?> restoredB = registryB.find(s1);
        assertTrue(restoredB.isPresent(), "B must recover symbol from shared snapshot");

        // seek은 snapshotOffset+1로 적용되어야 함
        verify(consumerB, atLeastOnce()).seek(p1, 201L);

        // p0는 여전히 A에 남아야 함(리밸런싱에서 안 뺏겼으니까)
        assertTrue(registryA.find(s0).isPresent(), "A should keep p0 symbol");

        // B는 p0를 갖고 있지 않아야 함
        assertTrue(registryB.find(s0).isEmpty(), "B must not be polluted with p0 symbol");

        // cleanup
        queueB.stopAccepting();
        queueB.close();
    }

    // ===== helper =====

    private SnapshotWriteQueue buildWriteQueue(SnapshotPathResolver resolver, ObjectMapper om) {
        SnapshotPayloadCodec codec = new JacksonSnapshotPayloadCodec(om);
        LocalSnapshotFileStore store = new LocalSnapshotFileStore(resolver, new AtomicFileWriter());
        SnapshotRetentionManager retention = new SnapshotRetentionManager(new SnapshotFileNameParser(), resolver, 20);
        SnapshotWriteWorker worker = new SnapshotWriteWorker(codec, store, retention);
        return new SnapshotWriteQueue(1000, worker);
    }

    private PartitionStateService buildStateService(
            SnapshotPathResolver resolver,
            ObjectMapper om,
            OrderBookRegistry registry,
            PartitionCountProvider countProvider,
            SnapshotCoordinator coordinator
    ) {
        SnapshotLoader loader = new SnapshotLoader(resolver, om, 5);
        SnapshotRestorer restorer = new SnapshotRestorer();
        return new PartitionStateService(loader, restorer, registry, countProvider, coordinator);
    }

    private static String pickSymbolForPartition(int targetPartition, int partitionCount, String prefix) {
        for (int i = 0; i < 200_000; i++) {
            String s = prefix + i;
            if (SnapshotCryptoUtils.resolve(s, partitionCount) == targetPartition) return s;
        }
        throw new IllegalStateException("cannot pick symbol for partition=" + targetPartition);
    }

    @Test
    void revoked_then_assigned_again_should_recover_again_and_seek_once() throws Exception {
        String topic = "orders.in";
        int partitionCount = 12;
        PartitionCountProvider countProvider = t -> partitionCount;

        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();

        OrderBookRegistry registry = new OrderBookRegistry(countProvider);
        SnapshotWriteQueue queue = buildWriteQueue(resolver, om);
        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, new SnapshotAssembler("engine-test"), queue);
        PartitionStateService state = buildStateService(resolver, om, registry, countProvider, coordinator);
        AssignedPartitionTracker tracker = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listener = new SnapshotRecoveryOnAssign(state, tracker, topic);

        TopicPartition p1 = new TopicPartition(topic, 1);

        String sym = pickSymbolForPartition(1, partitionCount, "RRA-");
        registry.getOrCreate(sym);

        long now = System.currentTimeMillis();
        coordinator.forceSnapshot(topic, 1, 200L, now);
        queue.stopAccepting();
        queue.close();

        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumer = mock(Consumer.class);

        // 1) assigned → seek
        listener.onPartitionsAssigned(consumer, List.of(p1));
        verify(consumer, atLeastOnce()).seek(p1, 201L);

        // 2) revoked → cleanup
        listener.onPartitionsRevokedAfterCommit(consumer, List.of(p1));
        assertTrue(registry.find(sym).isEmpty(), "registry must be cleaned after revoke");

        // 3) assigned again → recover again
        listener.onPartitionsAssigned(consumer, List.of(p1));
        assertTrue(registry.find(sym).isPresent(), "should recover again");

        // seek가 다시 호출되는 건 정상(재-assign이니까)
        verify(consumer, atLeast(2)).seek(p1, 201L);
    }

    @Test
    void lost_during_recovery_should_skip_seek_and_cleanup_memory() throws Exception {
        String topic = "orders.in";
        int partitionCount = 12;
        PartitionCountProvider countProvider = t -> partitionCount;

        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();

        // 실제 registry 사용 (cleanup 검증)
        OrderBookRegistry registry = new OrderBookRegistry(countProvider);

        // snapshot 준비
        SnapshotWriteQueue queue = buildWriteQueue(resolver, om);
        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, new SnapshotAssembler("engine-test"), queue);

        int partition = 1;
        TopicPartition tp = new TopicPartition(topic, partition);

        String sym = pickSymbolForPartition(partition, partitionCount, "LOST-");
        registry.getOrCreate(sym);

        long now = System.currentTimeMillis();
        coordinator.forceSnapshot(topic, partition, 300L, now);
        queue.stopAccepting();
        queue.close();

        // 느린 recovery를 만들기 위해 loader를 spy로 감싸고 loadLatest에 sleep
        SnapshotLoader realLoader = new SnapshotLoader(resolver, om, 5);
        SnapshotLoader slowLoader = spy(realLoader);
        doAnswer(inv -> {
            Thread.sleep(300); // recovery 지연
            return inv.callRealMethod();
        }).when(slowLoader).loadLatest(eq(topic), eq(partition));

        PartitionStateService state = new PartitionStateService(
                slowLoader,
                new SnapshotRestorer(),
                registry,
                countProvider,
                coordinator
        );

        AssignedPartitionTracker tracker = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listener = new SnapshotRecoveryOnAssign(state, tracker, topic);

        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumer = mock(Consumer.class);

        Thread assignThread = new Thread(() -> listener.onPartitionsAssigned(consumer, List.of(tp)));
        assignThread.start();

        // assign 시작 직후 lost 발생
        Thread.sleep(50);
        listener.onPartitionsLost(consumer, List.of(tp));

        assignThread.join(2_000);

        // lost가 중간에 들어갔으니 seek은 스킵되어야 함(epoch invalidation)
        verify(consumer, never()).seek(eq(tp), anyLong());

        // cleanup 되었으니 registry는 비어야 함
        assertTrue(registry.find(sym).isEmpty(), "must cleanup memory on lost");
    }

    @Test
    void snapshot_exists_but_no_symbols_match_partition_should_not_seek_and_not_pollute_registry() throws Exception {
        String topic = "orders.in";
        int partition = 4;

        int partitionCountNow = 12;
        PartitionCountProvider countProvider = t -> partitionCountNow;

        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();

        // registry는 비어있게 시작
        OrderBookRegistry registry = new OrderBookRegistry(countProvider);

        // snapshot 파일을 "직접" 생성: partition=4 디렉토리에 두되,
        // 내용의 symbols는 모두 partition != 4가 되게 만든다.
        String out1 = pickSymbolForPartition(3, partitionCountNow, "OUTA-");
        String out2 = pickSymbolForPartition(5, partitionCountNow, "OUTB-");

        PartitionSnapshot ps = PartitionSnapshot.create(
                "sid-x",
                "engine-test",
                topic, partition,
                999L, System.currentTimeMillis(),
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                List.of(
                        OrderBookSnapshot.create(out1, 1L, 1L, List.of(), List.of()),
                        OrderBookSnapshot.create(out2, 1L, 1L, List.of(), List.of())
                )
        );

        // write worker로 저장(정상 checksum)
        SnapshotWriteQueue queue = buildWriteQueue(resolver, om);
        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, new SnapshotAssembler("engine-test"), queue);

        SnapshotWriteWorker worker = new SnapshotWriteWorker(
                new JacksonSnapshotPayloadCodec(om),
                new LocalSnapshotFileStore(resolver, new AtomicFileWriter()),
                new SnapshotRetentionManager(new SnapshotFileNameParser(), resolver, 20)
        );
        worker.write(new SnapshotWriteRequest(
                topic, partition, 999L, System.currentTimeMillis(),
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                ps
        ));

        PartitionStateService state = new PartitionStateService(
                new SnapshotLoader(resolver, om, 5),
                new SnapshotRestorer(),
                registry,
                countProvider,
                coordinator
        );

        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumer = mock(Consumer.class);

        AssignedPartitionTracker tracker = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listener = new SnapshotRecoveryOnAssign(state, tracker, topic);

        TopicPartition tp = new TopicPartition(topic, partition);
        listener.onPartitionsAssigned(consumer, List.of(tp));

        // seek은 없어야 함
        verify(consumer, never()).seek(eq(tp), anyLong());

        // registry 오염도 없어야 함
        assertTrue(registry.snapshotView().isEmpty(), "registry must not be polluted");
    }

    @Test
    void partial_revoke_should_cleanup_only_revoked_partition_and_other_partition_survives() throws Exception {
        String topic = "orders.in";
        int partitionCount = 12;
        PartitionCountProvider countProvider = t -> partitionCount;

        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();

        // A
        OrderBookRegistry registryA = new OrderBookRegistry(countProvider);
        SnapshotWriteQueue queueA = buildWriteQueue(resolver, om);
        SnapshotCoordinator coordinatorA = new SnapshotCoordinator(registryA, new SnapshotAssembler("engine-test"), queueA);
        PartitionStateService stateA = buildStateService(resolver, om, registryA, countProvider, coordinatorA);
        AssignedPartitionTracker trackerA = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listenerA = new SnapshotRecoveryOnAssign(stateA, trackerA, topic);

        // B
        OrderBookRegistry registryB = new OrderBookRegistry(countProvider);
        SnapshotWriteQueue queueB = buildWriteQueue(resolver, om);
        SnapshotCoordinator coordinatorB = new SnapshotCoordinator(registryB, new SnapshotAssembler("engine-test"), queueB);
        PartitionStateService stateB = buildStateService(resolver, om, registryB, countProvider, coordinatorB);
        AssignedPartitionTracker trackerB = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listenerB = new SnapshotRecoveryOnAssign(stateB, trackerB, topic);

        TopicPartition p0 = new TopicPartition(topic, 0);
        TopicPartition p1 = new TopicPartition(topic, 1);

        String s0 = pickSymbolForPartition(0, partitionCount, "P0-");
        String s1 = pickSymbolForPartition(1, partitionCount, "P1-");

        registryA.getOrCreate(s0);
        registryA.getOrCreate(s1);

        long now = System.currentTimeMillis();
        coordinatorA.forceSnapshot(topic, 0, 10L, now);
        coordinatorA.forceSnapshot(topic, 1, 20L, now);
        queueA.stopAccepting();
        queueA.close();

        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumerA = mock(Consumer.class);

        // A가 두 파티션을 받음
        listenerA.onPartitionsAssigned(consumerA, List.of(p0, p1));

        // partial revoke: p1만 뺏김
        listenerA.onPartitionsRevokedAfterCommit(consumerA, List.of(p1));

        // p0는 살아있어야 함
        assertTrue(registryA.find(s0).isPresent());
        // p1은 없어야 함
        assertTrue(registryA.find(s1).isEmpty());

        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumerB = mock(Consumer.class);

        // B가 p1만 받음
        listenerB.onPartitionsAssigned(consumerB, List.of(p1));

        // B는 p1 복구돼야 함
        assertTrue(registryB.find(s1).isPresent());
        // B는 p0 오염 없어야 함
        assertTrue(registryB.find(s0).isEmpty());
    }


    @Test
    void assigned_before_other_consumer_revoked_should_not_pollute_and_should_end_consistent() throws Exception {
        // Kafka 정상 시퀀스는 revoke -> assign이지만,
        // 테스트에서는 "cleanup 지연" 같은 비정상 순서를 가정해도 안전한지 확인한다.
        String topic = "orders.in";
        int partitionCount = 12;
        PartitionCountProvider countProvider = t -> partitionCount;

        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();

        // A
        OrderBookRegistry registryA = new OrderBookRegistry(countProvider);
        SnapshotWriteQueue queueA = buildWriteQueue(resolver, om);
        SnapshotCoordinator coordinatorA = new SnapshotCoordinator(registryA, new SnapshotAssembler("engine-test"), queueA);
        PartitionStateService stateA = buildStateService(resolver, om, registryA, countProvider, coordinatorA);
        AssignedPartitionTracker trackerA = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listenerA = new SnapshotRecoveryOnAssign(stateA, trackerA, topic);

        // B
        OrderBookRegistry registryB = new OrderBookRegistry(countProvider);
        SnapshotWriteQueue queueB = buildWriteQueue(resolver, om);
        SnapshotCoordinator coordinatorB = new SnapshotCoordinator(registryB, new SnapshotAssembler("engine-test"), queueB);
        PartitionStateService stateB = buildStateService(resolver, om, registryB, countProvider, coordinatorB);
        AssignedPartitionTracker trackerB = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listenerB = new SnapshotRecoveryOnAssign(stateB, trackerB, topic);

        int partition = 1;
        TopicPartition tp = new TopicPartition(topic, partition);

        String sym = pickSymbolForPartition(partition, partitionCount, "OOO-");
        registryA.getOrCreate(sym);

        long now = System.currentTimeMillis();
        coordinatorA.forceSnapshot(topic, partition, 200L, now);
        queueA.stopAccepting();
        queueA.close();

        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumerA = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumerB = mock(Consumer.class);

        // (1) A assigned (정상)
        listenerA.onPartitionsAssigned(consumerA, List.of(tp));
        assertTrue(registryA.find(sym).isPresent());

        // (2) 비정상/지연: B가 먼저 assigned 되는 상황을 가정
        listenerB.onPartitionsAssigned(consumerB, List.of(tp));
        assertTrue(registryB.find(sym).isPresent(), "B should recover from snapshot even if revoke is delayed");
        verify(consumerB, atLeastOnce()).seek(tp, 201L);

        // (3) 뒤늦게 A revoke/cleanup 수행
        listenerA.onPartitionsRevokedAfterCommit(consumerA, List.of(tp));
        assertTrue(registryA.find(sym).isEmpty(), "A must cleanup its memory after revoke even if delayed");

        // B는 여전히 정상 상태
        assertTrue(registryB.find(sym).isPresent(), "B must keep recovered symbol");

        queueB.stopAccepting();
        queueB.close();
    }

    @Test
    void recovery_should_skip_bad_candidates_and_seek_from_first_valid_snapshot() throws Exception {
        String topic = "orders.in";
        int partition = 1;
        int partitionCount = 12;
        PartitionCountProvider countProvider = t -> partitionCount;

        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();

        OrderBookRegistry registry = new OrderBookRegistry(countProvider);

        // (A) write a VALID snapshot at offset=300
        SnapshotWriteQueue queue = buildWriteQueue(resolver, om);
        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, new SnapshotAssembler("engine-test"), queue);

        String sym = pickSymbolForPartition(partition, partitionCount, "GOOD-");
        registry.getOrCreate(sym);

        long now = System.currentTimeMillis();
        coordinator.forceSnapshot(topic, partition, 300L, now);

        // drain to ensure files exist
        queue.stopAccepting();
        queue.close();

        // (B) create a NEWER bad-checksum candidate at offset=400
        //  - write a valid snapshot file, then overwrite checksum with WRONG
        LocalSnapshotFileStore store = new LocalSnapshotFileStore(resolver, new AtomicFileWriter());
        PartitionSnapshot psBadChecksum = PartitionSnapshot.create(
                "sid-bad-checksum",
                "engine-test",
                topic, partition,
                400L, now + 1,
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                List.of(OrderBookSnapshot.create(sym, 1L, 1L, List.of(), List.of()))
        );
        SnapshotPayloadCodec codec = new JacksonSnapshotPayloadCodec(om);
        var payloadBad = codec.encode(psBadChecksum, CompressionType.GZIP, ChecksumAlgorithm.SHA_256);
        SnapshotWriteResult badWrite = store.writeSnapshot(
                topic, partition,
                400L, now + 1,
                psBadChecksum.getSnapshotId(),
                payloadBad.gzippedJson(),
                payloadBad.sha256Hex()
        );
        // corrupt checksum
        Files.writeString(badWrite.checksumPath(), "WRONG_SHA  " + badWrite.snapshotPath().getFileName(), StandardCharsets.UTF_8);

        // (C) create a NEWEST invalid-JSON candidate at offset=500 with CORRECT checksum
        byte[] gzInvalid = gzip("not-a-json".getBytes(StandardCharsets.UTF_8));
        String shaInvalid = SnapshotCryptoUtils.sha256Hex(gzInvalid);
        store.writeSnapshot(topic, partition, 500L, now + 2, "sid-invalid-json", gzInvalid, shaInvalid);

        // now run recovery with fallbackCount >= 3
        SnapshotLoader loader = new SnapshotLoader(resolver, om, 10);
        PartitionStateService state = new PartitionStateService(loader, new SnapshotRestorer(), registry, countProvider, coordinator);

        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumer = mock(Consumer.class);
        AssignedPartitionTracker tracker = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listener = new SnapshotRecoveryOnAssign(state, tracker, topic);

        TopicPartition tp = new TopicPartition(topic, partition);
        listener.onPartitionsAssigned(consumer, List.of(tp));

        // newest(500) invalid JSON -> skip
        // next(400) checksum mismatch -> skip
        // next(300) valid -> recover and seek to 301
        verify(consumer, atLeastOnce()).seek(tp, 301L);
        assertTrue(registry.find(sym).isPresent(), "registry should contain recovered symbol from first valid snapshot");
    }

    @Test
    void partition_count_changed_should_not_seek_and_not_pollute_registry() throws Exception {
        String topic = "orders.in";
        int partition = 4;

        int oldCount = 12;
        int newCount = 16;

        // snapshot was created when partitionCount=12
        String symOld = pickSymbolForPartition(partition, oldCount, "PCCHG-");
        // ensure mapping differs under newCount
        int tries = 0;
        while (SnapshotCryptoUtils.resolve(symOld, newCount) == partition && tries++ < 1000) {
            symOld = pickSymbolForPartition(partition, oldCount, "PCCHG-" + tries + "-");
        }
        assertNotEquals(partition, SnapshotCryptoUtils.resolve(symOld, newCount), "symbol must move under new partitionCount for this test");

        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();

        // registry starts empty and uses NEW partitionCount
        PartitionCountProvider countProviderNow = t -> newCount;
        OrderBookRegistry registry = new OrderBookRegistry(countProviderNow);

        // write snapshot file directly into partition dir
        PartitionSnapshot ps = PartitionSnapshot.create(
                "sid-pcchg",
                "engine-test",
                topic, partition,
                777L, System.currentTimeMillis(),
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                List.of(OrderBookSnapshot.create(symOld, 1L, 1L, List.of(), List.of()))
        );

        SnapshotWriteWorker worker = new SnapshotWriteWorker(
                new JacksonSnapshotPayloadCodec(om),
                new LocalSnapshotFileStore(resolver, new AtomicFileWriter()),
                new SnapshotRetentionManager(new SnapshotFileNameParser(), resolver, 20)
        );
        worker.write(new SnapshotWriteRequest(
                topic, partition,
                777L, System.currentTimeMillis(),
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                ps
        ));

        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, new SnapshotAssembler("engine-test"), buildWriteQueue(resolver, om));
        PartitionStateService state = new PartitionStateService(
                new SnapshotLoader(resolver, om, 10),
                new SnapshotRestorer(),
                registry,
                countProviderNow,
                coordinator
        );

        @SuppressWarnings("unchecked")
        Consumer<Object, Object> consumer = mock(Consumer.class);
        AssignedPartitionTracker tracker = new AssignedPartitionTracker();
        SnapshotRecoveryOnAssign listener = new SnapshotRecoveryOnAssign(state, tracker, topic);

        TopicPartition tp = new TopicPartition(topic, partition);
        listener.onPartitionsAssigned(consumer, List.of(tp));

        // under new partitionCount, symbols in snapshot don't match this partition -> no seek, no registry pollution
        verify(consumer, never()).seek(eq(tp), anyLong());
        assertTrue(registry.snapshotView().isEmpty(), "registry must remain empty when partitionCount changed");
    }
}