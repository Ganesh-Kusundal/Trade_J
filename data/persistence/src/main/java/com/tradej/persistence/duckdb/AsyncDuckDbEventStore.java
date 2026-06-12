package com.tradej.persistence.duckdb;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.PoisonPillEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async wrapper around {@link DuckDbEventStore} that moves blocking JDBC writes
 * off the event dispatch thread onto a dedicated background thread with batching.
 */
public final class AsyncDuckDbEventStore implements DomainEventHandler<DomainEvent>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncDuckDbEventStore.class);

    private static final int DEFAULT_QUEUE_CAPACITY = 4096;
    private static final int DEFAULT_BATCH_SIZE = 64;
    private static final int DEFAULT_BATCH_WAIT_MS = 10;
    private static final int STOP_TIMEOUT_SECONDS = 5;

    private final DuckDbEventStore delegate;
    private final BlockingQueue<DomainEvent> queue;
    private final int batchSize;
    private final int batchWaitMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong droppedEventCount = new AtomicLong();
    private volatile Thread workerThread;

    private static final PoisonPillEvent POISON_PILL = new PoisonPillEvent();

    public AsyncDuckDbEventStore(DuckDbEventStore delegate) {
        this(delegate, DEFAULT_QUEUE_CAPACITY, DEFAULT_BATCH_SIZE, DEFAULT_BATCH_WAIT_MS);
    }

    public AsyncDuckDbEventStore(DuckDbEventStore delegate, int queueCapacity, int batchSize, int batchWaitMs) {
        this.delegate = delegate;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.batchWaitMs = batchWaitMs;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread worker = new Thread(this::drainLoop, "duckdb-event-writer");
            worker.setDaemon(true);
            workerThread = worker;
            worker.start();
            log.info("AsyncDuckDbEventStore started");
        }
    }

    public long droppedEventCount() {
        return droppedEventCount.get();
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (!queue.offer(event)) {
            droppedEventCount.incrementAndGet();
            log.warn("AsyncDuckDbEventStore queue full — dropping event type={}", event.getClass().getSimpleName());
        }
    }

    @Override
    public void close() throws Exception {
        running.set(false);
        if (workerThread != null) {
            queue.put(POISON_PILL);
            workerThread.interrupt();
            workerThread.join(STOP_TIMEOUT_SECONDS * 1000L);
        }
        drainRemainingOnShutdown();
        delegate.close();
    }

    private void drainLoop() {
        List<DomainEvent> batch = new ArrayList<>(batchSize);
        while (running.get() || !queue.isEmpty()) {
            batch.clear();
            try {
                DomainEvent first = queue.poll(batchWaitMs, TimeUnit.MILLISECONDS);
                if (first == null || first instanceof PoisonPillEvent) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            for (DomainEvent event : batch) {
                try {
                    delegate.onEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to ingest event type={}: {}", event.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
    }

    private void drainRemainingOnShutdown() {
        List<DomainEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        for (DomainEvent event : remaining) {
            if (event instanceof PoisonPillEvent) continue;
            try {
                delegate.onEvent(event);
            } catch (Exception e) {
                log.warn("Failed to ingest event on shutdown: {}", e.getMessage());
            }
        }
    }
}
