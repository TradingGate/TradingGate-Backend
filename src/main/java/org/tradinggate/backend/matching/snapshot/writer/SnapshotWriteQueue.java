package org.tradinggate.backend.matching.snapshot.writer;

import lombok.extern.log4j.Log4j2;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotWriteRequest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public class SnapshotWriteQueue implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final SnapshotWriteWorker worker;

    private final Object lifecycleLock = new Object();
    private volatile boolean accepting = true;

    private final Object drainLock = new Object();
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public SnapshotWriteQueue(
            int capacity,
            SnapshotWriteWorker worker
    ) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.worker = worker;

        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                r -> {
                    Thread t = new Thread(r, "snapshot-writer");
                    t.setDaemon(false);
                    return t;
                },
                (r, ex) -> {
                    throw new RejectedExecutionException("snapshot queue full or executor shutting down");
                }
        );
    }

    public boolean enqueue(SnapshotWriteRequest request) {
        return submitInternal(request, false);
    }

    public boolean enqueueForce(SnapshotWriteRequest request) {
        return submitInternal(request, true);
    }

    private boolean submitInternal(SnapshotWriteRequest request, boolean force) {
        if (request == null) return false;

        synchronized (lifecycleLock) {
            if (!force && !accepting) return false;
            if (executor.isShutdown() || executor.isTerminated()) return false;

            // IMPORTANT: increment before submit to avoid a race where the task is dequeued
            // (queue empty) but hasn't yet incremented inFlight, causing awaitDrained() to
            // return true too early.
            inFlight.incrementAndGet();
            try {
                executor.execute(() -> {
                    try {
                        worker.write(request);
                    } catch (Exception e) {
                        log.error("[SNAPSHOT] write failed (best-effort) reason={}", e.toString(), e);
                    } finally {
                        inFlight.decrementAndGet();
                        synchronized (drainLock) {
                            drainLock.notifyAll();
                        }
                    }
                });
                return true;
            } catch (RejectedExecutionException e) {
                // bounded queue full OR shutting down
                inFlight.decrementAndGet();
                synchronized (drainLock) {
                    drainLock.notifyAll();
                }
                if (force) {
                    log.warn("[SNAPSHOT] drop FORCE write request (queue full) topic={}, partition={}, offset={}, accepting={} ",
                            request.topic(), request.partition(), request.lastProcessedOffset(), accepting);
                } else {
                    log.warn("[SNAPSHOT] drop write request (queue full) topic={}, offset={}",
                            request.topic(), request.lastProcessedOffset());
                }
                return false;
            }
        }
    }

    public void stopAccepting() {
        synchronized (lifecycleLock) {
            accepting = false;
        }
    }

    public boolean awaitDrained(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
        synchronized (drainLock) {
            while (true) {
                boolean empty = executor.getQueue().isEmpty();
                boolean idle = (inFlight.get() == 0);
                if (empty && idle) {
                    return true;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    drainLock.wait(Math.min(remaining, 200L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    @Override
    public void close() {
        stopAccepting();

        executor.shutdown();

        boolean drained = awaitDrained(3_000);
        if (!drained) {
            log.warn("[SNAPSHOT] close drain timeout (best-effort) remainingQueue={}, inFlight={}",
                    executor.getQueue().size(), inFlight.get());
        }

        try {
            boolean terminated = executor.awaitTermination(2_000, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!terminated) {
                log.warn("[SNAPSHOT] executor termination timeout -> shutdownNow (best-effort)");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        } finally {
            if (!executor.getQueue().isEmpty() || inFlight.get() != 0) {
                log.warn("[SNAPSHOT] closed with remaining work (best-effort) remainingQueue={}, inFlight={}",
                        executor.getQueue().size(), inFlight.get());
            }
        }
    }
}
