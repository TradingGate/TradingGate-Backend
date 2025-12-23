package org.tradinggate.backend.matching.snapshot.retention;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotFileMeta;
import org.tradinggate.backend.matching.snapshot.io.SnapshotPathResolver;
import org.tradinggate.backend.matching.snapshot.util.SnapshotFileNameParser;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
public class SnapshotRetentionManager {

    private final SnapshotFileNameParser snapshotFileNameParser;
    private final SnapshotPathResolver resolver;
    private final int keepLatest;

    public void applyRetention(String topic, int partition) throws IOException {
        Path dir = resolver.partitionDir(topic, partition);
        if (!Files.exists(dir)) return;

        List<SnapshotFileMeta> metas = listSnapshotMetas(dir, partition);

        metas.sort(Comparator
                .comparingLong(SnapshotFileMeta::offset).reversed()
                .thenComparingLong(SnapshotFileMeta::createdAtMillis).reversed()
                .thenComparing(SnapshotFileMeta::snapshotId)
        );

        if (metas.size() <= keepLatest) return;

        for (int i = keepLatest; i < metas.size(); i++) {
            deleteSnapshotAndChecksum(topic, partition, metas.get(i).path());
        }
    }

    private List<SnapshotFileMeta> listSnapshotMetas(Path dir, int partition) throws IOException {
        List<SnapshotFileMeta> metas = new ArrayList<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "snapshot_*.json.gz")) {
            for (Path p : ds) {
                SnapshotFileMeta meta = snapshotFileNameParser.parse(p, partition);
                if (meta != null) metas.add(meta);
                else log.warn("[SNAPSHOT] skip unknown snapshot filename={}", p.getFileName());
            }
        }
        return metas;
    }

    private void deleteSnapshotAndChecksum(String topic, int partition, Path snapshotFile) {
        try {
            Files.deleteIfExists(snapshotFile);

            Path checksum = resolver.checksumFilePath(topic, partition, snapshotFile.getFileName().toString());
            Files.deleteIfExists(checksum);

            log.info("[SNAPSHOT] retention delete topic={}, partition={}, file={}",
                    topic, partition, snapshotFile.getFileName());
        } catch (Exception e) {
            log.warn("[SNAPSHOT] retention delete failed topic={}, partition={}, file={}, err={}",
                    topic, partition, snapshotFile.getFileName(), e.toString(), e);
        }
    }
}
