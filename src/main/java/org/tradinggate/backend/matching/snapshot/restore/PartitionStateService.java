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

@Log4j2
@RequiredArgsConstructor
public class PartitionStateService {

    private final SnapshotLoader loader;
    private final SnapshotRestorer restorer;
    private final OrderBookRegistry orderBookRegistry;
    private final PartitionCountProvider partitionCountProvider;
    private final SnapshotCoordinator snapshotCoordinator;
    private final PartitionOffsetTracker offsetTracker;

    public PartitionRecoveryResult recoverOnPartitionAssigned(String topic, int partition, BooleanSupplier isStillCurrent) {
        if (topic == null || topic.isBlank()) return PartitionRecoveryResult.none();

        Optional<LoadedSnapshot> loadedOpt = loader.loadLatest(topic, partition);
        if (loadedOpt.isEmpty()) return PartitionRecoveryResult.none();

        if (isStillCurrent != null && !isStillCurrent.getAsBoolean()) {
            log.info("[SNAPSHOT] skip recovery apply (no longer current) topic={}, partition={}", topic, partition);
            return PartitionRecoveryResult.none();
        }

        LoadedSnapshot loaded = loadedOpt.get();

        List<OrderBook> restoredBooks = restorer.restorePartition(loaded.snapshot());

        int partitionCount = partitionCountProvider.partitionCount(topic);

        List<OrderBook> acceptedBooks = new ArrayList<>();
        for (OrderBook b : restoredBooks) {
            if (b == null || b.getSymbol() == null || b.getSymbol().isBlank()) continue;

            if (SnapshotCryptoUtils.resolve(b.getSymbol(), partitionCount) != partition) continue;

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

        if (acceptedBooks.isEmpty()) {
            log.info("[SNAPSHOT] recovered snapshot exists but no symbols matched topic={}, partition={} (skip seek calc)",
                    topic, partition);
            return PartitionRecoveryResult.none();
        }

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

    public PartitionCleanupResult cleanupOnPartitionUnassigned(String topic, int partition, String reason) {
        if (topic == null || topic.isBlank()) return PartitionCleanupResult.none(reason);
        if (partition < 0) return PartitionCleanupResult.none(reason);

        if ("revokedAfterCommit".equals(reason)) {
            long lastProcessedOffset = offsetTracker.getLastProcessedOffset(topic, partition);
            if (lastProcessedOffset >= 0) {
                snapshotCoordinator.forceSnapshot(topic, partition, lastProcessedOffset, System.currentTimeMillis());
            }
        }

        List<String> removedSymbols = orderBookRegistry.removeAllByPartition(topic, partition);
        snapshotCoordinator.clearPolicyForPartition(partition);

        offsetTracker.clear(topic, partition);

        log.info("[SNAPSHOT] {} cleanup topic={}, partition={}, removedSymbolsCount={}",
                reason, topic, partition, removedSymbols.size());

        return new PartitionCleanupResult(topic, partition, reason, removedSymbols);
    }
}
