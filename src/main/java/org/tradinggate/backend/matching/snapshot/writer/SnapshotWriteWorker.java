package org.tradinggate.backend.matching.snapshot.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotPayload;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteRequest;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteResult;
import org.tradinggate.backend.matching.snapshot.io.LocalSnapshotFileStore;
import org.tradinggate.backend.matching.snapshot.retention.SnapshotRetentionManager;

@Log4j2
@RequiredArgsConstructor
public class SnapshotWriteWorker {

    private final SnapshotPayloadCodec payloadCodec;
    private final LocalSnapshotFileStore fileStore;
    private final SnapshotRetentionManager retentionManager;

    public SnapshotWriteResult write(SnapshotWriteRequest req) throws Exception {
        if (req == null || req.snapshot() == null) {
            throw new IllegalArgumentException("SnapshotWriteRequest/snapshot must not be null");
        }

        SnapshotPayload payload = payloadCodec.encode(req.snapshot(), req.compression(), req.checksumAlg());

        SnapshotWriteResult result = fileStore.writeSnapshot(
                req.topic(),
                req.partition(),
                req.lastProcessedOffset(),
                req.createdAtMillis(),
                req.snapshot().getSnapshotId(),
                payload.gzippedJson(),
                payload.sha256Hex()
        );

        try {
            retentionManager.applyRetention(req.topic(), req.partition());
        } catch (Exception e) {
            log.warn("[SNAPSHOT] retention failed (best-effort) topic={}, partition={}, err={}",
                    req.topic(), req.partition(), e.toString(), e);
        }

        log.info("[SNAPSHOT] saved topic={}, partition={}, offset={}, file={}",
                req.topic(), req.partition(), req.lastProcessedOffset(), result.snapshotPath().getFileName());

        return result;
    }
}
