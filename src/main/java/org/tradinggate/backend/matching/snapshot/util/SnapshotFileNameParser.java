package org.tradinggate.backend.matching.snapshot.util;

import org.tradinggate.backend.matching.snapshot.dto.SnapshotFileMeta;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnapshotFileNameParser {

    private static final Pattern SNAPSHOT_FILE_PATTERN =
            Pattern.compile("^snapshot_(\\d+)_(\\d+)_([A-Za-z0-9\\-]+)\\.json\\.gz$");

    public static SnapshotFileMeta parse(Path fullPath, int partition) {
        String fileName = fullPath.getFileName().toString();
        Matcher m = SNAPSHOT_FILE_PATTERN.matcher(fileName);
        if (!m.matches()) return null;

        long offset = Long.parseLong(m.group(1));
        long createdAtMillis = Long.parseLong(m.group(2));
        String snapshotId = m.group(3);

        return new SnapshotFileMeta(fullPath, offset, createdAtMillis, snapshotId, partition);
    }
}
