package com.tradej.disruptor;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.disruptor.config.AsyncDispatchHandler;
import com.tradej.disruptor.config.DisruptorPipelineConfig;
import com.tradej.disruptor.config.GraphPipelineDisruptorHandler;
import com.tradej.disruptor.config.GraphStrategyDisruptorHandler;
import com.tradej.disruptor.config.StageTiming;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import com.tradej.pipeline.runtime.PipelineRuntimeBridge;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

    /**
     * Disruptor-backed event bus using Config A only: compiled graph runtime → async dispatch.
     * Legacy risk→candle→strategy→execution handler chains have been retired.
     */
    public final class DisruptorEventBus implements EventBus, DisruptorBusMetrics {
        private static final Logger log = LoggerFactory.getLogger(DisruptorEventBus.class);

        /**
         * Optional alert callback for paging on critical queue saturation and DLQ overflow.
         * Set via {@link #setAlertCallback(Consumer, Consumer)} after construction.
         */
        private volatile Consumer<String> criticalAlertCallback;
        private volatile Consumer<String> warningAlertCallback;

        private static final int MAX_SEEN_EVENTS = 200_000;
        // Increased from 4096 to 65536 to handle CandleDeveloping bursts from
        // the graph pipeline (CandleDeveloping emitted on every tick per symbol).
        // Previously 3600 ticks could overflow 4096-capacity queue and drop events.
        private static final int DOWNSTREAM_QUEUE_CAPACITY = 65536;
        static final int DEFAULT_DISPATCH_QUEUE_CAPACITY = 65536;

        private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers = new ConcurrentHashMap<>();

        private static final int EVICTION_INTERVAL = 1024;
        private final ConcurrentHashMap<String, Long> seenEvents = new ConcurrentHashMap<>();
        private final AtomicLong publishCounter = new AtomicLong();
        private final ScheduledExecutorService dedupPruner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dedup-pruner");
            t.setDaemon(true);
            return t;
        });

        private final BlockingQueue<DomainEvent> downstreamQueue = new ArrayBlockingQueue<>(DOWNSTREAM_QUEUE_CAPACITY);

        private final Disruptor<MutableDomainEventEnvelope> disruptor;
        private final ExecutionHandler executionHandler;
        private final AsyncDispatchHandler dispatchStage;
        private volatile boolean started;
        private volatile Thread drainerThread;
        final int ringBufferSize;
        private final DeadLetterQueue deadLetterQueue;
    private final com.tradej.core.domain.port.EventWriteAheadLog writeAheadLog;

    // Re-entrancy guard: prevents deadlock when a subscriber calls publish()
    // from within the dispatch handler thread. Re-entrant events are routed
    // through the downstream queue instead of the ring buffer.
    private static final ThreadLocal<Boolean> IN_DISPATCH = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Marks the current thread as inside a subscriber dispatch. Called by
     * {@link AsyncDispatchHandler} before invoking subscriber callbacks so
     * that any re-entrant {@link #publish(DomainEvent)} from a subscriber is
     * routed through the downstream queue instead of the ring buffer,
     * preventing deadlock.
     */
    public static void markDispatchBegin() {
        IN_DISPATCH.set(Boolean.TRUE);
    }

    /** Clears the re-entrancy guard. Must be called in a finally block after {@link #markDispatchBegin()}. */
    public static void markDispatchEnd() {
        IN_DISPATCH.set(Boolean.FALSE);
    }

        /**
         * Creates a DisruptorEventBus from the given configuration.
         */
        public DisruptorEventBus(DisruptorPipelineConfig config) {
            if (config.pipelineRuntimeBridge() == null) {
                throw new IllegalArgumentException(
                        "pipelineRuntimeBridge is required — legacy Config B/C disruptor chains are retired");
            }
            this.executionHandler = config.executionHandler();
            this.ringBufferSize = 8192;
            this.deadLetterQueue = config.deadLetterQueue() == null ? DeadLetterQueue.noop() : config.deadLetterQueue();
            this.writeAheadLog = config.writeAheadLog() == null ? com.tradej.core.domain.port.EventWriteAheadLog.noop() : config.writeAheadLog();
            this.dispatchStage = new AsyncDispatchHandler(
                    subscribers,
                    DEFAULT_DISPATCH_QUEUE_CAPACITY,
                    config.stageTimings().dispatch(),
                    this.deadLetterQueue
            );
            this.disruptor = new Disruptor<>(
                    MutableDomainEventEnvelope::new,
                    ringBufferSize,
                    Executors.defaultThreadFactory(),
                    ProducerType.MULTI,
                    selectWaitStrategy(config.runtimeMode())
            );

            Consumer<DomainEvent> safePublisher = event -> {
                if (!downstreamQueue.offer(event)) {
                    this.deadLetterQueue.append("downstream-queue", event,
                            "Downstream event queue full (capacity=" + DOWNSTREAM_QUEUE_CAPACITY + ")");
                    log.error("DOWNSTREAM QUEUE FULL — dropping event type={} eventId={} queueSize={} capacity={} — PAGING",
                            event.getClass().getSimpleName(), event.eventId(),
                            downstreamQueue.size(), DOWNSTREAM_QUEUE_CAPACITY);
                    alertCritical("Downstream queue full — dropping events. Queue size=" + downstreamQueue.size());
                } else if (downstreamQueue.size() > DOWNSTREAM_QUEUE_CAPACITY * 0.75) {
                    log.warn("Downstream queue nearing capacity: {} / {} events — latency risk",
                            downstreamQueue.size(), DOWNSTREAM_QUEUE_CAPACITY);
                    alertWarning("Downstream queue at " + (downstreamQueue.size() * 100 / DOWNSTREAM_QUEUE_CAPACITY) + "% capacity");
                }
            };

            Consumer<DomainEvent> portfolioPublisher;
            if (config.portfolioEngine() != null) {
                portfolioPublisher = event -> config.portfolioEngine().onDomainEvent(event, safePublisher);
            } else {
                portfolioPublisher = safePublisher;
            }

            GraphStrategyDisruptorHandler graphStrategyStage = null;
            if (config.graphStrategySandbox() != null) {
                graphStrategyStage = new GraphStrategyDisruptorHandler(
                        config.graphStrategySandbox(), portfolioPublisher, config.stageTimings().strategy());
            }

            if (config.compileGraphOnInit()) {
                config.pipelineRuntimeBridge().compileHotPath(portfolioPublisher);
            }
            GraphPipelineDisruptorHandler graphStage = new GraphPipelineDisruptorHandler(
                    config.pipelineRuntimeBridge().runtimeRef(),
                    config.stageTimings().risk()
            );
            if (graphStrategyStage != null) {
                disruptor.handleEventsWith(graphStage).then(graphStrategyStage).then(dispatchStage);
            } else {
                disruptor.handleEventsWith(graphStage).then(dispatchStage);
            }
            log.info("DisruptorEventBus initialized ringBufferSize={} pipeline=graph-runtime{}→async-dispatch portfolio={} timing={} mode={} waitStrategy={}",
                    ringBufferSize, graphStrategyStage != null ? "→graph-strategy" : "",
                    config.portfolioEngine() != null, config.stageTimings() != StageTimings.NO_OP,
                    config.runtimeMode(), selectWaitStrategy(config.runtimeMode()).getClass().getSimpleName());

            dedupPruner.scheduleAtFixedRate(this::pruneOldEntries, 1, 1, TimeUnit.MINUTES);
        }

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        subscribers.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        List<DomainEventHandler<? extends DomainEvent>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("Publishing event type={} eventId={}", event.getClass().getSimpleName(), event.eventId());
        }
        // Re-entrancy guard: if called from within a dispatch handler (subscriber callback),
        // route through the downstream queue to prevent ring buffer deadlock.
        // Must be checked BEFORE dedup — the first publish already registered the dedup key,
        // so re-publishing from the drainer would otherwise be flagged as duplicate.
        if (IN_DISPATCH.get()) {
            if (!downstreamQueue.offer(event)) {
                deadLetterQueue.append("reentrant-queue", event,
                        "Re-entrant downstream queue full (capacity=" + DOWNSTREAM_QUEUE_CAPACITY + ")");
                log.error("RE-ENTRANT DOWNSTREAM QUEUE FULL — dropping event type={} eventId={} — PAGING",
                        event.getClass().getSimpleName(), event.eventId());
                alertCritical("Re-entrant queue full — dropping events. Event type=" + event.getClass().getSimpleName());
            }
            return;
        }
        if (isDuplicate(event)) {
            return;
        }
        // Write-ahead log: persist event before entering ring buffer for crash recovery
        try {
            writeAheadLog.write(event);
        } catch (Exception e) {
            log.warn("WAL write failed for event type={}: {}", event.getClass().getSimpleName(), e.getMessage());
        }
        disruptor.getRingBuffer().publishEvent((envelope, sequence) -> envelope.setEvent(event));
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        log.info("Starting DisruptorEventBus");
        started = true;
        Thread drainer = new Thread(this::drainDownstreamQueue, "downstream-publisher");
        drainer.setDaemon(true);
        drainerThread = drainer;
        drainer.start();

        dispatchStage.start();
        executionHandler.start();
        disruptor.start();
        log.info("DisruptorEventBus started subscribers={}", subscribers.size());
    }

    @Override
    public void stop() {
        log.info("Stopping DisruptorEventBus");
        started = false;

        if (drainerThread != null) {
            drainerThread.interrupt();
            try {
                drainerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for drainer thread to stop");
            }
        }

        executionHandler.stop();
        disruptor.shutdown();
        dispatchStage.stop();
        dedupPruner.shutdownNow();
        log.info("DisruptorEventBus stopped");
    }

    private void drainDownstreamQueue() {
        while (started || !downstreamQueue.isEmpty()) {
            try {
                DomainEvent event = downstreamQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    publish(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                List<DomainEvent> remaining = new java.util.ArrayList<>();
                downstreamQueue.drainTo(remaining);
                for (DomainEvent ev : remaining) {
                    publish(ev);
                }
                return;
            }
        }
    }

    /**
     * Recover events from the write-ahead log after a crash.
     * Replays all persisted events back through the event bus pipeline.
     * Must be called after {@link #start()}.
     *
     * @return number of events replayed
     */
    public long recoverFromWal() {
        if (!started) {
            throw new IllegalStateException("Bus must be started before recovery");
        }
        long count = writeAheadLog.replay(this::publish);
        if (count > 0) {
            log.info("Recovered {} events from write-ahead log", count);
        }
        return count;
    }

    public long ringBufferRemainingCapacity() {
        return disruptor.getRingBuffer().remainingCapacity();
    }

    public int ringBufferSize() {
        return ringBufferSize;
    }

    public int dispatchQueueDepth() {
        return dispatchStage.queueDepth();
    }

    public long dispatchDroppedEventCount() {
        return dispatchStage.droppedEventCount();
    }

    public int subscriberCount() {
        return subscribers.values().stream().mapToInt(List::size).sum();
    }

    public boolean isStarted() {
        return started;
    }

    public int downstreamQueueDepth() {
        return downstreamQueue.size();
    }

    boolean isDuplicate(DomainEvent event) {
        if (seenEvents.size() >= MAX_SEEN_EVENTS) {
            if ((publishCounter.incrementAndGet() & (EVICTION_INTERVAL - 1)) == 0) {
                long cutoff = System.currentTimeMillis() - Duration.ofSeconds(30).toMillis();
                seenEvents.values().removeIf(ts -> ts < cutoff);
            }
        }
        String key = dedupKey(event);
        Long previous = seenEvents.putIfAbsent(key, System.currentTimeMillis());
        return previous != null;
    }

    /**
     * Computes a source/sequence-keyed dedup key for the given event.
     * Market data events are keyed by symbol+segment+exchange timestamp to
     * suppress broker retransmissions. Order events are keyed by orderId+type
     * to suppress duplicate callbacks. All other events fall back to eventId.
     */
    private String dedupKey(DomainEvent event) {
        return switch (event) {
            case MarketTickEvent tick ->
                    "TICK:" + tick.symbol() + ":" + tick.segment() + ":" + tick.exchangeTimestampEpochMs() + ":" + tick.metadata().sequenceId();
            case DepthUpdateEvent depth ->
                    "DEPTH:" + depth.symbol() + ":" + depth.segment() + ":" + depth.exchangeTimestampMs() + ":" + depth.metadata().sequenceId();
            case OrderAccepted accepted ->
                    "ORDER:" + accepted.order().orderId() + ":OrderAccepted";
            case OrderFilled filled ->
                    "ORDER:" + filled.order().orderId() + ":OrderFilled";
            case OrderRejected rejected ->
                    "ORDER:" + rejected.order().orderId() + ":OrderRejected";
            default -> event.eventId();
        };
    }

    /**
     * Select the Disruptor {@link WaitStrategy} based on the runtime mode.
     * <ul>
     *   <li>LIVE → {@link BusySpinWaitStrategy} (lowest latency, highest CPU)</li>
     *   <li>REPLAY/BACKTEST → {@link YieldingWaitStrategy} (balanced latency/CPU)</li>
     *   <li>Other → {@link SleepingWaitStrategy} (lowest CPU, acceptable for CLI)</li>
     * </ul>
     */
    private static WaitStrategy selectWaitStrategy(com.tradej.core.domain.runtime.RuntimeMode mode) {
        return switch (mode) {
            case LIVE -> new BusySpinWaitStrategy();
            case REPLAY, BACKTEST -> new YieldingWaitStrategy();
        };
    }

    /**
     * Sets optional alert callbacks for paging on critical conditions
     * (queue saturation, DLQ overflow). Must be called before {@link #start()}.
     *
     * @param onCritical called with a human-readable message on critical conditions
     * @param onWarning  called with a human-readable message on warning conditions
     */
    public void setAlertCallback(Consumer<String> onCritical, Consumer<String> onWarning) {
        this.criticalAlertCallback = onCritical;
        this.warningAlertCallback = onWarning;
    }

    private void alertCritical(String message) {
        Consumer<String> cb = this.criticalAlertCallback;
        if (cb != null) {
            cb.accept(message);
        }
    }

    private void alertWarning(String message) {
        Consumer<String> cb = this.warningAlertCallback;
        if (cb != null) {
            cb.accept(message);
        }
    }

    private void pruneOldEntries() {
        long nowMs = System.currentTimeMillis();
        long ttlMs = Duration.ofMinutes(5).toMillis();
        int before = seenEvents.size();
        seenEvents.values().removeIf(timestamp -> (nowMs - timestamp) > ttlMs);
        int pruned = before - seenEvents.size();
        if (pruned > 0 && log.isTraceEnabled()) {
            log.trace("Pruned {} stale entries from dedup cache (size={})", pruned, seenEvents.size());
        }
    }
}
