package org.tradinggate.backend.matching.snapshot.io;

import java.nio.file.Path;

public class SnapshotPathResolver {

    private final Path baseDir;

    public SnapshotPathResolver(Path baseDir) {
        this.baseDir = baseDir;
    }

    public Path snapshotsRootDir() {
        return baseDir.resolve("snapshots");
    }

    public Path partitionDir(String topic, int partition) {
        return snapshotsRootDir().resolve(topic).resolve(String.valueOf(partition));
    }

    public String snapshotFileName(long offset, long createdAtMillis, String snapshotId) {
        return "snapshot_" + offset + "_" + createdAtMillis + "_" + snapshotId + ".json.gz";
    }

    public Path snapshotFilePath(String topic, int partition, String fileName) {
        return partitionDir(topic, partition).resolve(fileName);
    }

    public Path checksumFilePath(String topic, int partition, String snapshotFileName) {
        return partitionDir(topic, partition).resolve(snapshotFileName + ".sha256");
    }
}
