package org.tradinggate.backend.matching.snapshot.restore;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.tradinggate.backend.matching.snapshot.dto.LoadedSnapshot;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotFileMeta;
import org.tradinggate.backend.matching.snapshot.io.SnapshotPathResolver;
import org.tradinggate.backend.matching.snapshot.model.PartitionSnapshot;
import org.tradinggate.backend.matching.snapshot.util.SnapshotCryptoUtils;
import org.tradinggate.backend.matching.snapshot.util.SnapshotFileNameParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Log4j2
@RequiredArgsConstructor
public class SnapshotLoader {

    private final SnapshotPathResolver resolver;
    private final ObjectMapper objectMapper;
    private final int fallbackCount;

    public Optional<LoadedSnapshot> loadLatest(String topic, int partition) {
        Path dir = resolver.partitionDir(topic, partition);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return Optional.empty();
        }

        List<SnapshotFileMeta> candidates;
        try {
            candidates = listCandidates(dir, partition);
        } catch (Exception e) {
            log.warn("[SNAPSHOT] list candidates failed topic={}, partition={}, err={}",
                    topic, partition, e.toString(), e);
            return Optional.empty();
        }

        int limit = Math.min(fallbackCount, candidates.size());
        for (int i = 0; i < limit; i++) {
            SnapshotFileMeta meta = candidates.get(i);
            try {
                LoadedSnapshot loaded = tryLoadOne(topic, partition, meta);
                if (loaded != null) return Optional.of(loaded);
            } catch (Exception e) {
                log.warn("[SNAPSHOT] load failed topic={}, partition={}, file={}, err={}",
                        topic, partition, meta.path().getFileName(), e.toString());
            }
        }

        return Optional.empty();
    }

    private List<SnapshotFileMeta> listCandidates(Path dir, int partition) throws IOException {
        List<SnapshotFileMeta> metas = new ArrayList<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "snapshot_*.json.gz")) {
            for (Path p : ds) {
                SnapshotFileMeta meta = SnapshotFileNameParser.parse(p, partition);
                if (meta == null) continue;
                metas.add(meta);
            }
        }

        metas.sort(Comparator
                .comparingLong(SnapshotFileMeta::offset).reversed()
                .thenComparingLong(SnapshotFileMeta::createdAtMillis).reversed()
                .thenComparing(SnapshotFileMeta::snapshotId)
        );

        return metas;
    }

    private LoadedSnapshot tryLoadOne(String topic, int partition, SnapshotFileMeta meta) throws Exception {
        Path snapshotFile = meta.path();
        if (!Files.exists(snapshotFile)) return null;

        Path checksumFile = resolver.checksumFilePath(topic, partition, snapshotFile.getFileName().toString());
        if (!Files.exists(checksumFile)) {
            log.warn("[SNAPSHOT] checksum missing topic={}, partition={}, file={}", topic, partition, snapshotFile.getFileName());
            return null;
        }

        byte[] gzipped = Files.readAllBytes(snapshotFile);
        String actualSha = SnapshotCryptoUtils.sha256Hex(gzipped);

        String expectedSha = readExpectedSha256(checksumFile);
        if (expectedSha == null || !expectedSha.equalsIgnoreCase(actualSha)) {
            log.warn("[SNAPSHOT] checksum mismatch topic={}, partition={}, file={}, expected={}, actual={}",
                    topic, partition, snapshotFile.getFileName(), expectedSha, actualSha);
            return null;
        }

        byte[] jsonBytes = SnapshotCryptoUtils.gunzip(gzipped);

        PartitionSnapshot snapshot = objectMapper.readValue(jsonBytes, PartitionSnapshot.class);
        if (snapshot == null) return null;

        // snapshotId는 상위(PartitionSnapshot)에서 검증하는 게 맞음
        if (snapshot.getSnapshotId() != null && !snapshot.getSnapshotId().equals(meta.snapshotId())) {
            log.warn("[SNAPSHOT] snapshotId mismatch topic={}, partition={}, fileId={}, bodyId={}",
                    topic, partition, meta.snapshotId(), snapshot.getSnapshotId());
        }

        return new LoadedSnapshot(meta, snapshot);
    }

    private String readExpectedSha256(Path checksumFile) throws IOException {
        String s = Files.readString(checksumFile, StandardCharsets.UTF_8).trim();
        if (s.isBlank()) return null;
        int space = s.indexOf(' ');
        if (space <= 0) return s;
        return s.substring(0, space).trim();
    }
}
