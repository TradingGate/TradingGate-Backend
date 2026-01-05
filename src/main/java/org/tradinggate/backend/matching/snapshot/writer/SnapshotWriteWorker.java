package org.tradinggate.backend.matching.snapshot.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotPayload;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteRequest;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteResult;
import org.tradinggate.backend.matching.snapshot.io.LocalSnapshotFileStore;
import org.tradinggate.backend.matching.snapshot.retention.SnapshotRetentionManager;

/**
 * - SnapshotWriteRequest를 파일 스토리지에 영속화하는 단일 작업 단위(워커).
 * - encode(압축/체크섬) → 원자적 파일 쓰기 → retention 적용 순서로 수행.
 *
 * [정책]
 * - retention은 best-effort: 실패하더라도 스냅샷 저장 자체는 성공으로 처리.
 */
@Log4j2
@RequiredArgsConstructor
public class SnapshotWriteWorker {

    private final SnapshotPayloadCodec payloadCodec;
    private final LocalSnapshotFileStore fileStore;
    private final SnapshotRetentionManager retentionManager;

    /**
     * @param req 스냅샷 저장 요청(토픽/파티션/offset/생성시각/스냅샷 본문 포함)
     * @return 저장된 스냅샷 파일/체크섬 파일 경로
     * @sideEffects 스냅샷 파일(.json.gz)과 체크섬 파일(.sha256)을 로컬 디스크에 기록한다.
     *
     * [예외]
     * - encode/파일쓰기 실패는 상위에서 재시도/에러핸들링하도록 예외를 전파한다.
     */
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
