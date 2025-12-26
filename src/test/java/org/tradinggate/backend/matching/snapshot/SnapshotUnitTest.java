package org.tradinggate.backend.matching.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tradinggate.backend.matching.engine.kafka.PartitionCountProvider;
import org.tradinggate.backend.matching.engine.model.OrderBook;
import org.tradinggate.backend.matching.engine.model.OrderBookRegistry;
import org.tradinggate.backend.matching.snapshot.dto.*;
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
import org.tradinggate.backend.matching.snapshot.shutdown.PartitionOffsetTracker;
import org.tradinggate.backend.matching.snapshot.util.SnapshotAssembler;
import org.tradinggate.backend.matching.snapshot.util.SnapshotCryptoUtils;
import org.tradinggate.backend.matching.snapshot.util.SnapshotFileNameParser;
import org.tradinggate.backend.matching.snapshot.util.SnapshotRestorer;
import org.tradinggate.backend.matching.snapshot.writer.JacksonSnapshotPayloadCodec;
import org.tradinggate.backend.matching.snapshot.writer.SnapshotPayloadCodec;
import org.tradinggate.backend.matching.snapshot.writer.SnapshotWriteQueue;
import org.tradinggate.backend.matching.snapshot.writer.SnapshotWriteWorker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SnapshotUnitTest {

    @TempDir
    Path tempDir;

    @Test
    void parse_shouldExtractOffsetCreatedAtSnapshotId_andSetPartitionFromArg() {
        int partition = 3;
        Path p = Path.of("snapshot_100_1700000000000_abc-123.json.gz");

        SnapshotFileMeta meta = SnapshotFileNameParser.parse(p, partition);

        assertNotNull(meta);
        assertEquals(100L, meta.offset());
        assertEquals(1700000000000L, meta.createdAtMillis());
        assertEquals("abc-123", meta.snapshotId());
        assertEquals(partition, meta.partition());
        assertEquals(p, meta.path());
    }

    @Test
    void parse_shouldReturnNull_whenPatternMismatch() {
        assertNull(SnapshotFileNameParser.parse(Path.of("bad_name.json.gz"), 1));
        assertNull(SnapshotFileNameParser.parse(Path.of("snapshot_1_2_3_4.json.gz"), 1)); // group 개수 다름
    }

    @Test
    void loadLatest_shouldSkipBadChecksumAndLoadNextCandidate() throws Exception {
        ObjectMapper om = new ObjectMapper();
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);

        // loader는 partition dir 기준으로 조회
        int partition = 1;
        String topic = "orders.in";
        Path dir = resolver.partitionDir(topic, partition);
        Files.createDirectories(dir);

        // candidate1 (최신 offset) : checksum mismatch -> skip
        writeRawSnapshotFile(resolver, topic, partition,
                /*offset*/ 200, /*createdAt*/ 2000, "sid-bad",
                "BAD".getBytes(StandardCharsets.UTF_8),
                "WRONG_SHA"
        );

        // candidate2 (그 다음 최신) : 정상
        byte[] gz2 = "OK".getBytes(StandardCharsets.UTF_8);
        String sha2 = SnapshotCryptoUtils.sha256Hex(gz2);

        // 실제로는 gzip json이어야 하지만, loader는 sha 체크 후 gunzip+jackson을 타니까
        // 여기서는 최소한 “정상 gzip json”을 만들어줌
        PartitionSnapshot snap = PartitionSnapshot.create(
                "sid-ok",
                "engine-v1",
                topic, partition,
                199, 3000,
                SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP,
                ChecksumAlgorithm.SHA_256,
                List.of() // orderBooks empty ok
        );

        byte[] json = om.writeValueAsBytes(snap);
        byte[] gzJson = SnapshotCryptoUtils.gzip(json);
        String shaJson = SnapshotCryptoUtils.sha256Hex(gzJson);

        writeRawSnapshotFile(resolver, topic, partition,
                199, 3000, "sid-ok",
                gzJson, shaJson
        );

        SnapshotLoader loader = new SnapshotLoader(resolver, om, /*fallbackCount*/ 10);

        Optional<LoadedSnapshot> loaded = loader.loadLatest(topic, partition);
        assertTrue(loaded.isPresent());
        assertEquals("sid-ok", loaded.get().meta().snapshotId());
        assertEquals(199L, loaded.get().meta().offset());
        assertEquals("sid-ok", loaded.get().snapshot().getSnapshotId());
    }

    private void writeRawSnapshotFile(
            SnapshotPathResolver resolver,
            String topic,
            int partition,
            long offset,
            long createdAt,
            String snapshotId,
            byte[] gzippedPayload,
            String sha256
    ) throws Exception {
        String fileName = resolver.snapshotFileName(offset, createdAt, snapshotId);
        Path snapPath = resolver.snapshotFilePath(topic, partition, fileName);
        Path shaPath = resolver.checksumFilePath(topic, partition, fileName);

        Files.createDirectories(snapPath.getParent());
        Files.write(snapPath, gzippedPayload);

        String text = sha256 + "  " + snapPath.getFileName();
        Files.writeString(shaPath, text, StandardCharsets.UTF_8);
    }

    @Test
    void recoverOnPartitionAssigned_shouldRestoreBooksAndReturnStartOffset() throws Exception {
        ObjectMapper om = new ObjectMapper().findAndRegisterModules();
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);

        int partitionCount = 12;
        String symbol = "BTCUSDT";

        PartitionCountProvider countProvider = t -> partitionCount;
        OrderBookRegistry registry = new OrderBookRegistry(countProvider);

        SnapshotLoader loader = new SnapshotLoader(resolver, om, 5);
        SnapshotRestorer restorer = new SnapshotRestorer();


        // 실제 파일에 스냅샷 저장
        int partition = SnapshotCryptoUtils.resolve(symbol, partitionCount);
        String topic = "orders.in";
        long offset = 555;
        long createdAt = System.currentTimeMillis();
        String snapshotId = "sid-555";

        // orderBookSnapshot 1개만 넣어도 충분
        OrderBookSnapshot obs = OrderBookSnapshot.create(
                symbol,
                100, 200,
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
                List.of(obs)
        );

        // writer (테스트용 codec 사용)
        SnapshotPayloadCodec codec = new JacksonSnapshotPayloadCodec(om);
        LocalSnapshotFileStore store = new LocalSnapshotFileStore(resolver, new AtomicFileWriter());
        var retention = new SnapshotRetentionManager(new SnapshotFileNameParser(), resolver, 20);
        SnapshotWriteWorker worker = new SnapshotWriteWorker(codec, store, retention);

        SnapshotWriteRequest req = new SnapshotWriteRequest(
                topic, partition, offset, createdAt, SnapshotTriggerReason.TIME_TRIGGER,
                CompressionType.GZIP, ChecksumAlgorithm.SHA_256, ps
        );
        worker.write(req);

        SnapshotAssembler assembler = new SnapshotAssembler("engine-v1");
        SnapshotWriteQueue writeQueue = new SnapshotWriteQueue(10000, worker);
        SnapshotCoordinator coordinator = new SnapshotCoordinator(registry, assembler, writeQueue);

        PartitionOffsetTracker offsetTracker = new PartitionOffsetTracker();
        PartitionStateService svc = new PartitionStateService(loader, restorer, registry, countProvider, coordinator, offsetTracker);

        PartitionRecoveryResult rr = svc.recoverOnPartitionAssigned(topic, partition, () -> true);

        assertTrue(rr.recovered());
        assertEquals(offset + 1, rr.seekStartOffset());

        // registry에 들어갔는지 확인
        Optional<OrderBook> bookOpt = registry.find(symbol);
        assertTrue(bookOpt.isPresent());
        assertEquals(symbol, bookOpt.get().getSymbol());
    }

    @Test
    void applyRetention_shouldKeepOnlyLatestN() throws Exception {
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);
        SnapshotRetentionManager rm = new SnapshotRetentionManager(
                new SnapshotFileNameParser(),
                resolver,
                2
        );

        String topic = "orders.in";
        int partition = 1;

        // 3개 생성 (offset 10, 11, 12) -> 최신 2개만 남아야 함
        for (long off : new long[]{10, 11, 12}) {
            String file = resolver.snapshotFileName(off, 1000 + off, "sid-" + off);
            Path snap = resolver.snapshotFilePath(topic, partition, file);
            Path sha = resolver.checksumFilePath(topic, partition, file);

            Files.createDirectories(snap.getParent());
            Files.write(snap, ("x" + off).getBytes(StandardCharsets.UTF_8));
            Files.writeString(sha, SnapshotCryptoUtils.sha256Hex(Files.readAllBytes(snap)) + "  " + snap.getFileName());
        }

        rm.applyRetention(topic, partition);

        Path dir = resolver.partitionDir(topic, partition);
        long remain = Files.list(dir)
                .filter(p -> p.getFileName().toString().endsWith(".json.gz"))
                .count();

        assertEquals(2, remain);
    }

    @Test
    void writeSnapshot_shouldWriteGzAndChecksumAtomically() throws Exception {
        SnapshotPathResolver resolver = new SnapshotPathResolver(tempDir);
        AtomicFileWriter atomic = new AtomicFileWriter();
        LocalSnapshotFileStore store = new LocalSnapshotFileStore(resolver, atomic);

        String topic = "orders.in";
        int partition = 2;
        long offset = 123L;
        long createdAt = 1700000000000L;
        String snapshotId = "sid-1";

        byte[] gz = "dummy-gz".getBytes(StandardCharsets.UTF_8);
        String sha = SnapshotCryptoUtils.sha256Hex(gz);

        SnapshotWriteResult r = store.writeSnapshot(
                topic, partition, offset, createdAt, snapshotId, gz, sha
        );

        assertTrue(Files.exists(r.snapshotPath()));
        assertTrue(Files.exists(r.checksumPath()));

        byte[] actualGz = Files.readAllBytes(r.snapshotPath());
        assertArrayEquals(gz, actualGz);

        String checksumText = Files.readString(r.checksumPath(), StandardCharsets.UTF_8).trim();
        assertTrue(checksumText.startsWith(sha), "checksum 파일은 sha로 시작해야 함");
    }

}
