package com.tradej.feature.store;

import com.tradej.core.domain.event.DomainEvent;
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
 * Async wrapper around {@link DuckDbFeatureStore} that moves blocking JDBC writes
 * off the event dispatch thread onto a dedicated background thread with batching.
 *
 * <p>Ingested events are offered into a bounded {@link BlockingQueue}. A single
 * daemon background thread drains the queue in batches and forwards them to the
 * underlying {@link DuckDbFeatureStore}. This ensures that DuckDB I/O can never
 * stall the hot-path event dispatch (fixes FS-01).
 *
 * <p>If the queue fills up, events are dropped with a WARN-level log and a
 * dropped-event counter to provide explicit backpressure signalling.
 */
public final class AsyncDuckDbWriter implements DomainEventHandler<DomainEvent>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncDuckDbWriter.class);

    /** Default queue capacity before backpressure drops. */
    private static final int DEFAULT_QUEUE_CAPACITY = 4096;

    /** Default maximum number of events to drain in a single batch. */
    private static final int DEFAULT_BATCH_SIZE = 64;

    /** Default maximum time (ms) to wait for a batch to fill. */
    private static final int DEFAULT_BATCH_WAIT_MS = 10;

    /** Shutdown drain timeout in seconds. */
    private static final int STOP_TIMEOUT_SECONDS = 5;

    private final DuckDbFeatureStore delegate;
    private final BlockingQueue<DomainEvent> queue;
    private final int batchSize;
    private final int batchWaitMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong droppedEventCount = new AtomicLong();
    private final AtomicLong ingestedCount = new AtomicLong();
    private volatile Thread workerThread;

    /** Poison-pill sentinel for shutdown. */
    private static final class PoisonPill implements DomainEvent {
        @Override
        public com.tradej.core.domain.event.EventMetadata metadata() {
            return com.tradej.core.domain.event.EventMetadata.root();
        }

        @Override
        public void accept(com.tradej.core.domain.event.DomainEventVisitor visitor) {
            // PoisonPill is internal and doesn't need to be visited by risk/engine
        }
    }

    private static final PoisonPill POISON_PILL = new PoisonPill();

    /**
     * Creates an async writer with default queue capacity (4096), batch size (64),
     * and batch wait (10 ms).
     */
    public AsyncDuckDbWriter(DuckDbFeatureStore delegate) {
        this(delegate, DEFAULT_QUEUE_CAPACITY, DEFAULT_BATCH_SIZE, DEFAULT_BATCH_WAIT_MS);
    }

    /**
     * Creates an async writer with custom parameters.
     *
     * @param delegate     the underlying DuckDB feature store
     * @param queueCapacity maximum number of events that can be queued before drops
     * @param batchSize    maximum events to drain per batch
     * @param batchWaitMs  maximum milliseconds to wait for a batch to fill
     */
    public AsyncDuckDbWriter(DuckDbFeatureStore delegate, int queueCapacity, int batchSize, int batchWaitMs) {
        this.delegate = delegate;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.batchWaitMs = batchWaitMs;
    }

    /** Start the background drain thread. */
    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread worker = new Thread(this::drainLoop, "duckdb-feature-writer");
            worker.setDaemon(true);
            workerThread = worker;
            worker.start();
            log.info("AsyncDuckDbWriter started queueCapacity={} batchSize={} batchWaitMs={}",
                    queue.remainingCapacity() + queue.size(), batchSize, batchWaitMs);
        }
    }

    /**
     * Non-blocking ingest: offers the event to the bounded queue. If the queue
     * is full, the event is dropped and the dropped-event counter is incremented.
     */
    @Override
    public void onEvent(DomainEvent event) {
        if (!queue.offer(event)) {
            droppedEventCount.incrementAndGet();
            log.warn("AsyncDuckDbWriter queue full — dropping event type={} eventId={} droppedTotal={}",
                    event.getClass().getSimpleName(), event.eventId(), droppedEventCount.get());
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (workerThread != null) {
            // Deliver poison pill via put() which blocks until the worker drains
            // enough events to make space. This avoids dropping an event (unlike
            // poll+offer which would discard one event when the queue is full).
            try {
                queue.put(POISON_PILL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            workerThread.interrupt();
            try {
                workerThread.join(STOP_TIMEOUT_SECONDS * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Drain any events the worker may have missed (safety net after join timeout).
        drainRemainingOnShutdown();
        try {
            delegate.close();
        } catch (Exception e) {
            log.warn("Error closing DuckDbFeatureStore: {}", e.getMessage());
        }
        log.info("AsyncDuckDbWriter stopped ingested={} dropped={}", ingestedCount.get(), droppedEventCount.get());
    }

    /** Returns the number of events dropped due to queue full. */
    public long droppedEventCount() {
        return droppedEventCount.get();
    }

    /** Returns the number of events successfully ingested. */
    public long ingestedCount() {
        return ingestedCount.get();
    }

    /** Returns the current queue depth. */
    public int queueDepth() {
        return queue.size();
    }

    // ── Private helpers ──

    private void drainLoop() {
        List<DomainEvent> batch = new ArrayList<>(batchSize);
        while (running.get() || !queue.isEmpty()) {
            batch.clear();
            // Block until first event arrives, then drain up to batchSize
            try {
                DomainEvent first = queue.poll(batchWaitMs, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue; // timed out — loop back and check running flag
                }
                if (first instanceof PoisonPill) {
                    break;
                }
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // Process the batch
            for (DomainEvent event : batch) {
                try {
                    delegate.feed(event);
                    ingestedCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("Failed to ingest event type={} eventId={}: {}",
                            event.getClass().getSimpleName(), event.eventId(), e.getMessage());
                }
            }
        }
    }

    /** Drain any remaining events on shutdown, skipping the poison pill sentinel. */
    private void drainRemainingOnShutdown() {
        List<DomainEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            log.info("Draining {} remaining events on shutdown", remaining.size());
            for (DomainEvent event : remaining) {
                // Skip poison pill — it's a shutdown sentinel, not a real event
                if (event instanceof PoisonPill) {
                    continue;
                }
                try {
                    delegate.feed(event);
                    ingestedCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("Failed to ingest event on shutdown drain type={}: {}",
                            event.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
    }
}
