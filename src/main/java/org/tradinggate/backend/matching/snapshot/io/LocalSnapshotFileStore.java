package org.tradinggate.backend.matching.snapshot.io;

import lombok.RequiredArgsConstructor;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@RequiredArgsConstructor
public class LocalSnapshotFileStore {

    private final SnapshotPathResolver pathResolver;
    private final AtomicFileWriter atomicFileWriter;

    public SnapshotWriteResult writeSnapshot(
            String topic,
            int partition,
            long offset,
            long createdAtMillis,
            String snapshotId,
            byte[] gzippedJsonPayload,
            String sha256Hex
    ) throws IOException {
        String fileName = pathResolver.snapshotFileName(offset, createdAtMillis, snapshotId);

        Path snapshotPath = pathResolver.snapshotFilePath(topic, partition, fileName);
        Path checksumPath = pathResolver.checksumFilePath(topic, partition, fileName);

        atomicFileWriter.writeAtomic(snapshotPath, gzippedJsonPayload);

        byte[] checksumBytes = (sha256Hex + "  " + snapshotPath.getFileName()).getBytes(StandardCharsets.UTF_8);
        atomicFileWriter.writeAtomic(checksumPath, checksumBytes);

        return new SnapshotWriteResult(topic, partition, snapshotPath, checksumPath);
    }
}
