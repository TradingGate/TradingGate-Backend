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


/**
 * - 파티션별 스냅샷 파일을 최신 N개(keepLatest)만 유지하고 나머지는 삭제.
 *
 * [운영 의도]
 * - 디스크 사용량이 무한히 증가하는 것을 방지.
 * - 복구 관점에서는 "최신 스냅샷 + Kafka replay"가 기본이므로, 오래된 스냅샷은 운영상 가치가 낮다.
 *
 * [주의]
 * - retention은 best-effort이며, 삭제 실패가 시스템 정합성에 영향을 주지 않아야 한다.
 */
@Log4j2
@RequiredArgsConstructor
public class SnapshotRetentionManager {

    private final SnapshotFileNameParser snapshotFileNameParser;
    private final SnapshotPathResolver resolver;
    private final int keepLatest;

    /**
     * @param topic 스냅샷 대상 토픽
     * @param partition 스냅샷 대상 파티션
     * @sideEffects 오래된 snapshot(.json.gz) 및 checksum(.sha256) 파일을 삭제할 수 있다.
     *
     * [정책]
     * - 정렬 기준은 (offset desc → createdAt desc → snapshotId)로 통일한다.
     *   동일 offset에서 여러 파일이 생겼을 때도 결정적으로 "최신 후보"를 고르게 하기 위함.
     */
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

        // keepLatest 이후는 전부 삭제 대상. (메타는 최신순 정렬됨)
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

            // 스냅샷과 체크섬은 한 세트로 관리(고아 파일 방지).
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
