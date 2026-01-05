package org.tradinggate.backend.matching.snapshot.restore;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.tradinggate.backend.matching.engine.kafka.PartitionCountProvider;
import org.tradinggate.backend.matching.engine.model.OrderBook;
import org.tradinggate.backend.matching.engine.model.OrderBookRegistry;
import org.tradinggate.backend.matching.snapshot.SnapshotCoordinator;
import org.tradinggate.backend.matching.snapshot.dto.LoadedSnapshot;
import org.tradinggate.backend.matching.snapshot.dto.PartitionCleanupResult;
import org.tradinggate.backend.matching.snapshot.dto.PartitionRecoveryResult;
import org.tradinggate.backend.matching.snapshot.dto.RecoveredOne;
import org.tradinggate.backend.matching.snapshot.shutdown.PartitionOffsetTracker;
import org.tradinggate.backend.matching.snapshot.util.SnapshotCryptoUtils;
import org.tradinggate.backend.matching.snapshot.util.SnapshotRestorer;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * - 파티션 단위 상태(오더북, 스냅샷 정책, 마지막 처리 offset)를 복구/정리
 * - 리밸런싱 이벤트 리스너(SnapshotRecoveryOnAssign)에서만 호출되며,
 *   여기서 "스냅샷 기반 seek 위치"를 계산
 */
@Log4j2
@RequiredArgsConstructor
public class PartitionStateService {

    private final SnapshotLoader loader;
    private final SnapshotRestorer restorer;
    private final OrderBookRegistry orderBookRegistry;
    private final PartitionCountProvider partitionCountProvider;
    private final SnapshotCoordinator snapshotCoordinator;
    private final PartitionOffsetTracker offsetTracker;

    /**
     * @param topic          대상 토픽
     * @param partition      대상 파티션
     * @param isStillCurrent 리밸런싱 경쟁조건 방지용(현재 epoch 유효성 확인). null이면 체크 생략.
     * @return 복구 결과(복구 여부, seek 시작 offset, 복구된 심볼 정보)
     * @sideEffects 성공 시 orderBookRegistry에 복구된 오더북들이 put 된다.
     */
    public PartitionRecoveryResult recoverOnPartitionAssigned(String topic, int partition, BooleanSupplier isStillCurrent) {
        if (topic == null || topic.isBlank()) return PartitionRecoveryResult.none();

        Optional<LoadedSnapshot> loadedOpt = loader.loadLatest(topic, partition);
        if (loadedOpt.isEmpty()) return PartitionRecoveryResult.none();

        // assign 직후에도 revoke/lost가 들어올 수 있으니 apply 전에 확인
        if (isStillCurrent != null && !isStillCurrent.getAsBoolean()) {
            log.info("[SNAPSHOT] skip recovery apply (no longer current) topic={}, partition={}", topic, partition);
            return PartitionRecoveryResult.none();
        }

        LoadedSnapshot loaded = loadedOpt.get();

        List<OrderBook> restoredBooks = restorer.restorePartition(loaded.snapshot());

        int partitionCount = partitionCountProvider.partitionCount(topic);

        // 스냅샷에 들어있더라도 "현재 파티션 소유 범위"에 해당하는 심볼만 accept
        // (토픽 파티션 수 변경/구성 변화가 있을 수 있어 방어적으로 필터)
        List<OrderBook> acceptedBooks = new ArrayList<>();
        for (OrderBook b : restoredBooks) {
            if (b == null || b.getSymbol() == null || b.getSymbol().isBlank()) continue;

            if (SnapshotCryptoUtils.resolve(b.getSymbol(), partitionCount) != partition) continue;

            // apply 중 epoch가 바뀌면, 지금까지 적재한 것들을 롤백 후 종료
            // (in-memory 롤백이므로 완전 트랜잭션은 아니지만, 최소한의 일관성은 지킨다)
            if (isStillCurrent != null && !isStillCurrent.getAsBoolean()) {
                for (OrderBook acceptedBook : acceptedBooks) {
                    orderBookRegistry.remove(acceptedBook.getSymbol());
                }
                log.info("[SNAPSHOT] skip recovery apply (epoch changed) topic={}, partition={}, rolledBack={}",
                        topic, partition, acceptedBooks.size());
                return PartitionRecoveryResult.none();
            }
            orderBookRegistry.put(b);
            acceptedBooks.add(b);

        }

        // 스냅샷 파일은 있지만 현재 파티션으로 귀속되는 심볼이 없으면 seek 계산 X
        if (acceptedBooks.isEmpty()) {
            log.info("[SNAPSHOT] recovered snapshot exists but no symbols matched topic={}, partition={} (skip seek calc)",
                    topic, partition);
            return PartitionRecoveryResult.none();
        }

        // 스냅샷이 반영한 마지막 offset 다음부터 재처리
        long startOffset = loaded.meta().offset() + 1L;

        List<RecoveredOne> details = acceptedBooks.stream()
                .map(b -> new RecoveredOne(
                        b.getSymbol(),
                        loaded.meta().offset(),
                        startOffset,
                        loaded.meta().snapshotId(),
                        loaded.meta().path().getFileName().toString()
                ))
                .toList();

        log.info("[SNAPSHOT] recovered partition topic={}, partition={}, recoveredSymbols={}, startOffset={}",
                topic, partition, restoredBooks.size(), startOffset);

        return PartitionRecoveryResult.recovered(restoredBooks.size(), startOffset, details);
    }

    /**
     * @param reason "revokedAfterCommit" | "lost" 등 호출 원인
     * @return 정리 결과
     * @sideEffects (조건부) 마지막 스냅샷 요청 enqueue, 오더북/정책/오프셋 상태 제거
     */
    public PartitionCleanupResult cleanupOnPartitionUnassigned(String topic, int partition, String reason) {
        if (topic == null || topic.isBlank()) return PartitionCleanupResult.none(reason);
        if (partition < 0) return PartitionCleanupResult.none(reason);

        // revokedAfterCommit은 커밋이 끝난 상태로 보므로 마지막 스냅샷을 시도.
        // lost는 커밋/처리 경계가 불명확할 수 있어 강제 스냅샷을 피한다.
        if ("revokedAfterCommit".equals(reason)) {
            long lastProcessedOffset = offsetTracker.getLastProcessedOffset(topic, partition);
            if (lastProcessedOffset >= 0) {
                snapshotCoordinator.forceSnapshot(topic, partition, lastProcessedOffset, System.currentTimeMillis());
            }
        }

        // 파티션 소유권이 끝났으면 해당 파티션의 오더북/정책/오프셋 상태는 제거
        List<String> removedSymbols = orderBookRegistry.removeAllByPartition(topic, partition);
        snapshotCoordinator.clearPolicyForPartition(partition);

        offsetTracker.clear(topic, partition);

        log.info("[SNAPSHOT] {} cleanup topic={}, partition={}, removedSymbolsCount={}",
                reason, topic, partition, removedSymbols.size());

        return new PartitionCleanupResult(topic, partition, reason, removedSymbols);
    }
}
