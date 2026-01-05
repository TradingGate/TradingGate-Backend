package org.tradinggate.backend.matching.snapshot.model;

public final class SnapshotConstants {

    private SnapshotConstants() {}

    /** snapshot 최소 시간 간격 (ms) */
    public static final long MIN_SNAPSHOT_INTERVAL_MILLIS = 10_000L; // 10초

    /** snapshot 최대 이벤트 수 */
    public static final long MAX_EVENTS_BEFORE_SNAPSHOT = 50_000L;

    /** fallback 시도 최대 개수 */
    public static final int MAX_FALLBACK_SNAPSHOTS = 20;

    /** 심볼별 snapshot 보관 개수 */
    public static final int RETENTION_PER_SYMBOL = 20;

    /** snapshot writer 재시도 최대 횟수 */
    public static final int SNAPSHOT_WRITE_MAX_RETRY = 3;

    /** snapshot writer 재시도 backoff (ms) */
    public static final long SNAPSHOT_WRITE_RETRY_BACKOFF_MILLIS = 1_000L;

    /** snapshot 압축 방식 */
    public static final String COMPRESSION_TYPE = "GZIP";

    /** checksum 알고리즘 */
    public static final String CHECKSUM_ALGORITHM = "SHA-256";

}
