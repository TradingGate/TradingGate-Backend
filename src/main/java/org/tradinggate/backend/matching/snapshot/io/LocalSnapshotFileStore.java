package org.tradinggate.backend.matching.snapshot.io;

import lombok.RequiredArgsConstructor;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * - 스냅샷 payload와 체크섬을 로컬 파일로 저장
 *
 * [정책]
 * - snapshot 파일(.json.gz)과 checksum 파일(.sha256)은 동일 디렉토리에 세트로 저장
 * - 실제 파일 쓰기는 AtomicFileWriter를 통해 "부분 파일"이 남지 않도록 한다.
 */
@RequiredArgsConstructor
public class LocalSnapshotFileStore {

    private final SnapshotPathResolver pathResolver;
    private final AtomicFileWriter atomicFileWriter;

    /**
     * @sideEffects
     * - snapshot 파일과 checksum 파일을 디스크에 기록한다.
     */
    public SnapshotWriteResult writeSnapshot(
            String topic,
            int partition,
            long offset,
            long createdAtMillis,
            String snapshotId,
            byte[] gzippedJsonPayload,
            String sha256Hex
    ) throws IOException {
        if (topic == null || topic.isBlank() || partition < 0 || offset < 0)
            throw new IllegalArgumentException("Invalid snapshot key: topic=" + topic + ", partition=" + partition + ", offset=" + offset);

        String fileName = pathResolver.snapshotFileName(offset, createdAtMillis, snapshotId);

        Path snapshotPath = pathResolver.snapshotFilePath(topic, partition, fileName);
        Path checksumPath = pathResolver.checksumFilePath(topic, partition, fileName);

        atomicFileWriter.writeAtomic(snapshotPath, gzippedJsonPayload);

        byte[] checksumBytes = (sha256Hex + "  " + snapshotPath.getFileName()).getBytes(StandardCharsets.UTF_8);
        atomicFileWriter.writeAtomic(checksumPath, checksumBytes);

        return new SnapshotWriteResult(topic, partition, snapshotPath, checksumPath);
    }
}
