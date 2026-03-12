package org.tradinggate.backend.matching.snapshot.util;


import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.tradinggate.backend.matching.snapshot.model.SnapshotConstants;
import org.tradinggate.backend.matching.snapshot.model.e.SnapshotTriggerReason;

public class SnapshotPolicy {

    private final AtomicLong lastSnapshotAtMillis = new AtomicLong(0L);
    private final AtomicLong eventsSinceLastSnapshot = new AtomicLong(0L);

    public void onEventProcessed() {
        eventsSinceLastSnapshot.incrementAndGet();
    }

    public Optional<SnapshotTriggerReason> shouldSnapshot(long nowMillis) {
        long lastAt = lastSnapshotAtMillis.get();
        long events = eventsSinceLastSnapshot.get();

        if (lastAt == 0L) {
            return Optional.of(SnapshotTriggerReason.TIME_TRIGGER);
        }

        if (nowMillis - lastAt >= SnapshotConstants.MIN_SNAPSHOT_INTERVAL_MILLIS) {
            return Optional.of(SnapshotTriggerReason.TIME_TRIGGER);
        }

        if (events >= SnapshotConstants.MAX_EVENTS_BEFORE_SNAPSHOT) {
            return Optional.of(SnapshotTriggerReason.COUNT_TRIGGER);
        }

        return Optional.empty();
    }

    public void onSnapshotTaken(long nowMillis) {
        this.lastSnapshotAtMillis.set(nowMillis);
        this.eventsSinceLastSnapshot.set(0L);
    }

    public long getLastSnapshotAtMillis() {
        return lastSnapshotAtMillis.get();
    }

    public long getEventsSinceLastSnapshot() {
        return eventsSinceLastSnapshot.get();
    }
}
