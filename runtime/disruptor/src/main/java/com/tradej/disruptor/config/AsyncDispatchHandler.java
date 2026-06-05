package com.tradej.disruptor.config;

import com.lmax.disruptor.EventHandler;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.support.MdcHelper;
import com.tradej.disruptor.MutableDomainEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Non-blocking dispatch handler that moves subscriber dispatch (Chronicle Queue
 * serialization, DuckDB JDBC writes — blocking I/O) off the Disruptor consumer
 * thread onto a dedicated background thread.
 *
 * <p>The Disruptor thread only enqueues a reference to the event into a bounded
 * {@link BlockingQueue}. A single background thread drains the queue and
 * dispatches to registered subscribers. This ensures that blocking I/O in
 * subscribers (e.g., {@code DuckDbEventStore}) cannot stall the hot path.
 *
 * <p>If the dispatch queue fills up, events are dropped with a WARN-level log
 * and metric counter to provide explicit backpressure signalling.
 *
 * <p>Replaces {@link SubscriberDispatchHandler} as the final Disruptor stage.
 */
public final class AsyncDispatchHandler implements EventHandler<MutableDomainEventEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(AsyncDispatchHandler.class);
    private static final int DEFAULT_QUEUE_CAPACITY = 4096;
    private static final long STOP_TIMEOUT_SECONDS = 10;

    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers;
    private final BlockingQueue<DomainEvent> dispatchQueue;
    private final ExecutorService dispatcher;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong droppedEventCount = new AtomicLong();
    private final StageTiming timing;
    private final DeadLetterQueue deadLetterQueue;

    // ── Poison-pill sentinel for shutdown signaling ──
    // Used instead of InterruptedException or poll(timeout) so that drainLoop()
    // can block on take() for zero-latency event delivery (fixes A-01).
    private static final class PoisonPill implements DomainEvent {
        @Override
        public EventMetadata metadata() {
            return EventMetadata.root();
        }

        @Override
        public void accept(com.tradej.core.domain.event.DomainEventVisitor visitor) {
            // PoisonPill is internal and doesn't need to be visited by risk/engine
        }
    }

    private static final PoisonPill POISON_PILL = new PoisonPill();

    /**
     * Creates an async dispatch handler with the given subscriber map and
     * a default queue capacity of 4096.
     */
    public AsyncDispatchHandler(Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers) {
        this(subscribers, DEFAULT_QUEUE_CAPACITY, StageTiming.noOp(), DeadLetterQueue.noop());
    }

    /**
     * Creates an async dispatch handler with a custom queue capacity.
     *
     * @param subscribers   the subscriber map (same structure as
     *                      {@link SubscriberDispatchHandler})
     * @param queueCapacity maximum number of events that can be queued before
     *                      backpressure drops are triggered
     */
    public AsyncDispatchHandler(
            Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers,
            int queueCapacity
    ) {
        this(subscribers, queueCapacity, StageTiming.noOp(), DeadLetterQueue.noop());
    }

    /**
     * Creates an async dispatch handler with a custom queue capacity and stage timing.
     */
    public AsyncDispatchHandler(
            Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers,
            int queueCapacity,
            StageTiming timing
    ) {
        this(subscribers, queueCapacity, timing, DeadLetterQueue.noop());
    }

    /**
     * Creates an async dispatch handler with DLQ support for dropped events.
     */
    public AsyncDispatchHandler(
            Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers,
            int queueCapacity,
            StageTiming timing,
            DeadLetterQueue deadLetterQueue
    ) {
        this.subscribers = subscribers;
        this.dispatchQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.timing = timing;
        this.deadLetterQueue = deadLetterQueue == null ? DeadLetterQueue.noop() : deadLetterQueue;
        this.dispatcher = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "async-dispatch-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Start the background dispatch thread. */
    public void start() {
        if (running.compareAndSet(false, true)) {
            dispatcher.submit(this::drainLoop);
            log.info("AsyncDispatchHandler started queueCapacity={}", dispatchQueue.remainingCapacity() + dispatchQueue.size());
        }
    }

    /**
     * Gracefully stop the background dispatch thread. Offers a poison pill to
     * unblock the {@code take()} in the drain loop, then waits for the thread
     * to complete. Falls back to shutdownNow on timeout.
     */
    public void stop() {
        running.set(false);
        dispatchQueue.offer(POISON_PILL); // unblock take()
        dispatcher.shutdown(); // polite — no interrupt, tasks finish
        try {
            if (!dispatcher.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                // Timed out — force stop
                dispatcher.shutdownNow();
                drainRemainingOnShutdown();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dispatcher.shutdownNow();
            drainRemainingOnShutdown();
        }
    }

    /** Returns the number of events dropped due to queue full. */
    public long droppedEventCount() {
        return droppedEventCount.get();
    }

    /** Returns the current number of events waiting to be dispatched. */
    public int queueDepth() {
        return dispatchQueue.size();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onEvent(MutableDomainEventEnvelope envelope, long sequence, boolean endOfBatch) throws Exception {
        DomainEvent event = envelope.event();
        if (event == null) {
            return;
        }

        // Fast path: offer to queue. Never block the Disruptor thread.
        if (!dispatchQueue.offer(event)) {
            droppedEventCount.incrementAndGet();
            deadLetterQueue.append("async-dispatch", event, "Dispatch queue full");
            log.warn("Dispatch queue full — dropping event type={} eventId={} droppedTotal={}",
                    event.getClass().getSimpleName(), event.eventId(), droppedEventCount);
        }

        envelope.clear();
    }

    // ── Private helpers ──

    /** Drain remaining items from the queue and dispatch them. */
    private void drainRemainingOnShutdown() {
        List<DomainEvent> remaining = new ArrayList<>();
        dispatchQueue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            log.info("Draining {} remaining events on shutdown", remaining.size());
            for (DomainEvent event : remaining) {
                dispatch(event);
            }
        }
    }

    /** Dispatch a single event to all matching subscribers. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatch(DomainEvent event) {
        long start = System.nanoTime();
        MdcHelper.enrich(event, "async-dispatch");
        try {
            for (Map.Entry<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> entry : subscribers.entrySet()) {
                if (entry.getKey().isAssignableFrom(event.getClass())) {
                    for (DomainEventHandler handler : entry.getValue()) {
                        handler.onEvent(event);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Async dispatch error processing event type={} eventId={}",
                    event.getClass().getSimpleName(), event.eventId(), e);
        } finally {
            timing.record(System.nanoTime() - start);
            MdcHelper.clear();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void drainLoop() {
        while (running.get() || !dispatchQueue.isEmpty()) {
            try {
                DomainEvent event = dispatchQueue.take();
                // Check for poison pill sentinel before dispatch
                if (event instanceof PoisonPill) {
                    break;
                }
                dispatch(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Drain remaining events before exiting
                drainRemainingOnShutdown();
                return;
            }
        }
    }
}
