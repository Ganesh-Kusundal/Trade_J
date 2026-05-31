package com.tradej.disruptor;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.disruptor.config.GraphPipelineDisruptorHandler;
import com.tradej.disruptor.config.AsyncDispatchHandler;
import com.tradej.disruptor.config.CandleAggregationDisruptorHandler;
import com.tradej.disruptor.config.ExecutionDisruptorHandler;
import com.tradej.disruptor.config.FeatureSyncDisruptorHandler;
import com.tradej.disruptor.config.GraphStrategyDisruptorHandler;
import com.tradej.disruptor.config.PositionRiskDisruptorHandler;
import com.tradej.disruptor.config.StageTiming;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.disruptor.config.StrategyDisruptorHandler;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.GraphStrategySandbox;
import com.tradej.strategy.service.StrategyEngine;
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

public final class DisruptorEventBus implements EventBus, DisruptorBusMetrics {
    private static final Logger log = LoggerFactory.getLogger(DisruptorEventBus.class);

    private static final int MAX_SEEN_EVENTS = 200_000;
    private static final int DOWNSTREAM_QUEUE_CAPACITY = 4096;
    private static final int DEFAULT_DISPATCH_QUEUE_CAPACITY = 4096;

    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers = new ConcurrentHashMap<>();

    // Bounded dedup cache: periodic pruning instead of per-publish O(n) scan
    // Throttle: only run age-based eviction every N calls to avoid O(n) scan on every hot-path publish.
    private static final int EVICTION_INTERVAL = 1024;
    private final ConcurrentHashMap<String, Long> seenEvents = new ConcurrentHashMap<>();
    private final AtomicLong publishCounter = new AtomicLong();
    private final ScheduledExecutorService dedupPruner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dedup-pruner");
        t.setDaemon(true);
        return t;
    });

    // Downstream event queue: breaks the re-entrant ring buffer publish pattern.
    // Pipeline stage callbacks offer() here instead of calling publishEvent()
    // directly on the ring buffer, preventing self-deadlock when the buffer is full.
    private final BlockingQueue<DomainEvent> downstreamQueue = new ArrayBlockingQueue<>(DOWNSTREAM_QUEUE_CAPACITY);

    private final Disruptor<MutableDomainEventEnvelope> disruptor;
    private final ExecutionHandler executionHandler;
    private final AsyncDispatchHandler dispatchStage;
    private volatile boolean started;
    private volatile Thread drainerThread;
    private final int ringBufferSize;
    private final DeadLetterQueue deadLetterQueue;

    public DisruptorEventBus(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler
    ) {
        this(positionRiskHandler, candleAggregationService, strategyEngine, executionHandler, null, StageTimings.NO_OP);
    }

    public DisruptorEventBus(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine
    ) {
        this(positionRiskHandler, candleAggregationService, strategyEngine, executionHandler, portfolioEngine, StageTimings.NO_OP, null, DeadLetterQueue.noop());
    }

    public DisruptorEventBus(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings
    ) {
        this(positionRiskHandler, candleAggregationService, strategyEngine, executionHandler, portfolioEngine, stageTimings, null, DeadLetterQueue.noop());
    }

    public DisruptorEventBus(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue
    ) {
        this(positionRiskHandler, candleAggregationService, strategyEngine, executionHandler,
                portfolioEngine, stageTimings, hotPathFeatureStore, deadLetterQueue, null, false);
    }

    public DisruptorEventBus(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue,
            PipelineRuntimeBridge pipelineRuntimeBridge
    ) {
        this(positionRiskHandler, candleAggregationService, strategyEngine, null, executionHandler,
                portfolioEngine, stageTimings, hotPathFeatureStore, deadLetterQueue, pipelineRuntimeBridge, true);
    }

    public DisruptorEventBus(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue,
            PipelineRuntimeBridge pipelineRuntimeBridge
    ) {
        this(positionRiskHandler, candleAggregationService, strategyEngine, graphStrategySandbox, executionHandler,
                portfolioEngine, stageTimings, hotPathFeatureStore, deadLetterQueue, pipelineRuntimeBridge, true);
    }

    public DisruptorEventBus(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue,
            PipelineRuntimeBridge pipelineRuntimeBridge,
            boolean compileGraphOnInit
    ) {
        this(positionRiskHandler, candleAggregationService, strategyEngine, null, executionHandler,
                portfolioEngine, stageTimings, hotPathFeatureStore, deadLetterQueue,
                pipelineRuntimeBridge, compileGraphOnInit);
    }

    public DisruptorEventBus(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            StrategyEngine strategyEngine,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore hotPathFeatureStore,
            DeadLetterQueue deadLetterQueue,
            PipelineRuntimeBridge pipelineRuntimeBridge,
            boolean compileGraphOnInit
    ) {
        this.executionHandler = executionHandler;
        this.ringBufferSize = 8192;
        this.deadLetterQueue = deadLetterQueue == null ? DeadLetterQueue.noop() : deadLetterQueue;
        this.dispatchStage = new AsyncDispatchHandler(
                subscribers,
                DEFAULT_DISPATCH_QUEUE_CAPACITY,
                stageTimings.dispatch(),
                this.deadLetterQueue
        );
        this.disruptor = new Disruptor<>(
                MutableDomainEventEnvelope::new,
                ringBufferSize,
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,
                new BusySpinWaitStrategy()
        );

        // Safe publisher for pipeline stage callbacks: offers to a bounded queue
        // instead of publishing directly to the ring buffer (fixes C-04 re-entrant deadlock).
        // A dedicated background thread drains this queue and publishes to the ring buffer.
        Consumer<DomainEvent> safePublisher = event -> {
            if (!downstreamQueue.offer(event)) {
                this.deadLetterQueue.append("downstream-queue", event,
                        "Downstream event queue full (capacity=" + DOWNSTREAM_QUEUE_CAPACITY + ")");
                log.warn("Downstream event queue full — dropping event type={} eventId={}",
                        event.getClass().getSimpleName(), event.eventId());
            }
        };

        // Wrap with portfolio engine when available
        Consumer<DomainEvent> portfolioPublisher;
        if (portfolioEngine != null) {
            portfolioPublisher = event -> portfolioEngine.onDomainEvent(event, safePublisher);
        } else {
            portfolioPublisher = safePublisher;
        }

        PositionRiskDisruptorHandler riskStage = new PositionRiskDisruptorHandler(positionRiskHandler, portfolioPublisher, stageTimings.risk());
        CandleAggregationDisruptorHandler candleStage = new CandleAggregationDisruptorHandler(candleAggregationService, portfolioPublisher, stageTimings.candle());
        StrategyDisruptorHandler strategyStage = new StrategyDisruptorHandler(strategyEngine, portfolioPublisher, stageTimings.strategy());
        ExecutionDisruptorHandler executionStage = new ExecutionDisruptorHandler(executionHandler, portfolioPublisher, stageTimings.execution());

        // Optional graph strategy stage for tick/depth/multi-event plugins
        GraphStrategyDisruptorHandler graphStrategyStage = null;
        if (graphStrategySandbox != null) {
            graphStrategyStage = new GraphStrategyDisruptorHandler(
                    graphStrategySandbox, portfolioPublisher, stageTimings.strategy());
        }

        if (pipelineRuntimeBridge != null) {
            if (compileGraphOnInit) {
                pipelineRuntimeBridge.compileHotPath(portfolioPublisher);
            }
            GraphPipelineDisruptorHandler graphStage = new GraphPipelineDisruptorHandler(
                    pipelineRuntimeBridge.runtimeRef(),
                    stageTimings.risk()
            );
            if (graphStrategyStage != null) {
                disruptor.handleEventsWith(graphStage).then(graphStrategyStage).then(dispatchStage);
            } else {
                disruptor.handleEventsWith(graphStage).then(dispatchStage);
            }
            log.info("DisruptorEventBus initialized ringBufferSize={} pipeline=graph-runtime{}→async-dispatch portfolio={} timing={}",
                    ringBufferSize, graphStrategyStage != null ? "→graph-strategy" : "",
                    portfolioEngine != null, stageTimings != StageTimings.NO_OP);
        } else if (hotPathFeatureStore != null) {
            FeatureSyncDisruptorHandler featureStage = new FeatureSyncDisruptorHandler(hotPathFeatureStore, StageTiming.noOp());
            if (graphStrategyStage != null) {
                disruptor.handleEventsWith(riskStage)
                        .then(candleStage)
                        .then(featureStage)
                        .then(strategyStage)
                        .then(graphStrategyStage)
                        .then(executionStage)
                        .then(dispatchStage);
            } else {
                disruptor.handleEventsWith(riskStage)
                        .then(candleStage)
                        .then(featureStage)
                        .then(strategyStage)
                        .then(executionStage)
                        .then(dispatchStage);
            }
            log.info("DisruptorEventBus initialized ringBufferSize={} pipeline=risk→candle→feature-sync→strategy{}→execution→async-dispatch portfolio={} timing={}",
                    ringBufferSize, graphStrategyStage != null ? "→graph-strategy" : "",
                    portfolioEngine != null, stageTimings != StageTimings.NO_OP);
        } else {
            if (graphStrategyStage != null) {
                disruptor.handleEventsWith(riskStage)
                        .then(candleStage)
                        .then(strategyStage)
                        .then(graphStrategyStage)
                        .then(executionStage)
                        .then(dispatchStage);
            } else {
                disruptor.handleEventsWith(riskStage)
                        .then(candleStage)
                        .then(strategyStage)
                        .then(executionStage)
                        .then(dispatchStage);
            }
            log.info("DisruptorEventBus initialized ringBufferSize={} pipeline=risk→candle→strategy{}→execution→async-dispatch portfolio={} timing={}",
                    ringBufferSize, graphStrategyStage != null ? "→graph-strategy" : "",
                    portfolioEngine != null, stageTimings != StageTimings.NO_OP);
        }

        // Schedule periodic dedup pruning (avoids O(n) scan on every publish)
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
        if (event == null || isDuplicate(event)) {
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("Publishing event type={} eventId={}", event.getClass().getSimpleName(), event.eventId());
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
        // Start the downstream queue drainer thread (started must be true before drainer runs)
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

        // 1. Stop and join the drainer thread first — this guarantees that all remaining
        // events in the downstreamQueue are drained and published to the ring buffer
        // BEFORE the disruptor is shut down.
        if (drainerThread != null) {
            drainerThread.interrupt();
            try {
                drainerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for drainer thread to stop");
            }
        }

        // 2. Stop the execution handler — no more signal/order placements accepted
        executionHandler.stop();

        // 3. Shutdown disruptor — blocks until all ring buffer events (including those
        // just published by the drainer on exit) are processed by all stage handlers.
        disruptor.shutdown();

        // 4. Stop cold-path dispatch and pruner
        dispatchStage.stop();
        dedupPruner.shutdownNow();
        log.info("DisruptorEventBus stopped");
    }

    /**
     * Drains the downstream event queue and publishes events to the ring buffer.
     * This runs on a dedicated thread so pipeline stage callbacks never block
     * the ring buffer consumer threads (fixes the re-entrant deadlock risk).
     */
    private void drainDownstreamQueue() {
        while (started || !downstreamQueue.isEmpty()) {
            try {
                DomainEvent event = downstreamQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    publish(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Drain remaining on shutdown
                List<DomainEvent> remaining = new java.util.ArrayList<>();
                downstreamQueue.drainTo(remaining);
                for (DomainEvent ev : remaining) {
                    publish(ev);
                }
                return;
            }
        }
    }

    // ── Metrics support ──

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

    /** Returns the number of events in the downstream queue. */
    public int downstreamQueueDepth() {
        return downstreamQueue.size();
    }

    // ── Dedup ──

    /**
     * Checks whether an event is a duplicate by its event ID.
     * When the cache reaches capacity, evicts only entries older than
     * 30 seconds rather than clearing the entire cache (fixes C-03).
     */
    private boolean isDuplicate(DomainEvent event) {
        if (seenEvents.size() >= MAX_SEEN_EVENTS) {
            // Age-based eviction: throttled to run every EVICTION_INTERVAL calls
            // to avoid O(n) iteration on every hot-path publish.
            if ((publishCounter.incrementAndGet() & (EVICTION_INTERVAL - 1)) == 0) {
                long cutoff = System.currentTimeMillis() - Duration.ofSeconds(30).toMillis();
                seenEvents.values().removeIf(ts -> ts < cutoff);
            }
        }
        Long previous = seenEvents.putIfAbsent(event.eventId(), System.currentTimeMillis());
        return previous != null;
    }

    /**
     * Periodic pruner runs every minute to clean entries older than 5 minutes.
     * Guards against long-term memory leak from stale event IDs.
     */
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
