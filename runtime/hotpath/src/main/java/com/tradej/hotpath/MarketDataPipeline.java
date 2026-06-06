package com.tradej.hotpath;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.support.MdcHelper;
import com.tradej.hotpath.rate.TokenBucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Pure Java orchestrator for the market data hot path.
 *
 * <p>This class manages the flow of market data events from the broker's
 * WebSocket feed into the Disruptor event pipeline. It is intentionally
 * free of Spring annotations or proxies so that the hot path can be
 * benchmarked, tested, and deployed without container overhead.
 *
 * <p>Usage:
 * <pre>{@code
 * MarketDataPipeline pipeline = new MarketDataPipeline(eventBus::publish);
 * pipeline.onMarketTickEvent(marketTickEvent);  // feeds into the disruptor pipeline
 * }</pre>
 *
 * <p>Thread safety: all public methods are safe for multi-threaded access
 * from the broker's WebSocket threads.
 *
 * <p>Metrics:
 * <ul>
 *   <li>{@link #totalTicksProcessed()} — cumulative tick count</li>
 *   <li>{@link #tickRate()} — exponential moving average ticks/second</li>
 *   <li>{@link #lastTickTimestampMs()} — timestamp of the last tick</li>
 *   <li>{@link #tickRateLimitedCount()} — number of ticks dropped by rate limiting</li>
 * </ul>
 *
 * <p>Optional rate limiting:
 * <ul>
 *   <li>Pass a {@link TokenBucket} to the constructor to cap the tick rate.
 *       Ticks that exceed the configured rate are silently dropped (shed).</li>
 *   <li>Pass {@code null} (or use the single-arg constructor) for unlimited throughput.</li>
 * </ul>
 */
public final class MarketDataPipeline {

    private static final Logger log = LoggerFactory.getLogger(MarketDataPipeline.class);

    /** Smoothing factor for the EMA tick rate. Higher = more responsive to spikes. */
    static final double RATE_ALPHA = 0.3;

    private final Consumer<DomainEvent> downstream;
    private final TokenBucket tickRateLimiter;
    private final AtomicLong tickCount;
    private final AtomicLong depthCount;
    private final AtomicLong tickRateLimitedCount;
    private volatile long lastTickTimestampMs;
    private final AtomicReference<RateState> rateState;

    /**
     * Creates an unlimited pipeline (no rate limiting).
     *
     * @param downstream consumer that forwards events into the event bus
     *                    (typically a reference to {@code DisruptorEventBus::publish})
     */
    public MarketDataPipeline(Consumer<DomainEvent> downstream) {
        this(downstream, null);
    }

    /**
     * Creates a pipeline with optional rate limiting.
     *
     * @param downstream       consumer that forwards events into the event bus
     * @param tickRateLimiter  optional rate limiter for tick shedding ({@code null} = unlimited)
     */
    public MarketDataPipeline(Consumer<DomainEvent> downstream, TokenBucket tickRateLimiter) {
        this.downstream = Objects.requireNonNull(downstream, "downstream must not be null");
        this.tickRateLimiter = tickRateLimiter;
        this.tickCount = new AtomicLong(0);
        this.depthCount = new AtomicLong(0);
        this.tickRateLimitedCount = new AtomicLong(0);
        this.lastTickTimestampMs = 0L;
        this.rateState = new AtomicReference<>(new RateState(0.0, 0L));
    }

    /**
     * Process an incoming tick received from the broker's market data feed.
     *
     * <p>The tick is forwarded into the Disruptor pipeline for candle aggregation,
     * feature computation, and strategy evaluation.
     *
     * @param tick the incoming market data tick
     * @deprecated Use {@link #onMarketTickEvent(MarketTickEvent)} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)

    /**
     * Process a canonical market tick event from the broker's market data feed.
     *
     * <p>This is the primary entry point for all market data ticks. The tick is
     * forwarded into the Disruptor pipeline for candle aggregation, feature
     * computation, and strategy evaluation. Supports depth extraction via
     * {@link DepthUpdateFactory#fromMarketTickEvent(MarketTickEvent)}.
     *
     * @param tick the canonical market data tick
     */
    public void onMarketTickEvent(MarketTickEvent tick) {
        if (tick == null) {
            log.warn("Ignoring null market tick event");
            return;
        }
        MdcHelper.enrich(tick, "market-data");
        try {
            if (tickRateLimiter != null && !tickRateLimiter.tryConsume()) {
                tickRateLimitedCount.incrementAndGet();
                log.debug("Tick rate limited, dropping tick symbol={} ltp={}",
                        tick.symbol(), tick.ltpPaisa());
                return;
            }
            tickCount.incrementAndGet();
            lastTickTimestampMs = tick.metadata().timestampMs();
            updateTickRate();
            log.trace("Processing market tick symbol={} ltp={} qty={}",
                    tick.symbol(), tick.ltpPaisa(), tick.lastTradeQuantity());
            downstream.accept(tick);
            DepthUpdateEvent depthUpdate = DepthUpdateFactory.fromMarketTickEvent(tick);
            if (depthUpdate != null) {
                depthCount.incrementAndGet();
                downstream.accept(depthUpdate);
            }
        } finally {
            MdcHelper.clear();
        }
    }

    /**
     * Process a standalone depth update (depth-only broker feeds).
     */
    public void onDepthUpdate(DepthUpdateEvent depth) {
        if (depth == null) {
            log.warn("Ignoring null depth update");
            return;
        }
        MdcHelper.enrich(depth, "market-depth");
        try {
            depthCount.incrementAndGet();
            downstream.accept(depth);
        } finally {
            MdcHelper.clear();
        }
    }

    public long totalDepthUpdatesProcessed() {
        return depthCount.get();
    }

    /**
     * Returns the total number of ticks processed since pipeline creation.
     */
    public long totalTicksProcessed() {
        return tickCount.get();
    }

    /**
     * Returns the exponential moving average tick rate (ticks per second).
     *
     * <p>Returns 0.0 until at least two ticks have been processed (no interval
     * to compute a rate from a single tick). The rate is smoothed using
     * {@value #RATE_ALPHA} as the EMA alpha factor.
     */
    public double tickRate() {
        return rateState.get().smoothedRate;
    }

    /**
     * Returns the wall-clock timestamp (epoch millis) of the most recently
     * processed tick, or 0 if no ticks have been processed yet.
     */
    public long lastTickTimestampMs() {
        return lastTickTimestampMs;
    }

    /**
     * Returns the number of ticks that have been dropped by rate limiting.
     */
    public long tickRateLimitedCount() {
        return tickRateLimitedCount.get();
    }

    /**
     * Returns the configured tick rate limiter, or {@code null} if no rate limiting
     * is active.
     */
    public TokenBucket tickRateLimiter() {
        return tickRateLimiter;
    }

    /**
     * Updates the EMA tick rate using the wall clock (System.nanoTime) for
     * real-time arrival rate measurement. Uses nanoTime to avoid clock skew
     * affecting rate computation.
     */
    private void updateTickRate() {
        long now = System.nanoTime();
        while (true) {
            RateState current = rateState.get();
            if (current.lastEventNanos == 0L) {
                // First tick — no interval to compute rate yet
                if (rateState.compareAndSet(current, new RateState(0.0, now))) {
                    return;
                }
            } else {
                double elapsedSec = (now - current.lastEventNanos) / 1_000_000_000.0;
                if (elapsedSec <= 0) {
                    return; // Same nanosecond tick, skip
                }
                double instantRate = 1.0 / elapsedSec;
                double smoothed = RATE_ALPHA * instantRate + (1.0 - RATE_ALPHA) * current.smoothedRate;
                if (rateState.compareAndSet(current, new RateState(smoothed, now))) {
                    return;
                }
            }
        }
    }

    /**
     * Immutable rate tracking state for lock-free CAS updates.
     *
     * @param smoothedRate   exponential moving average ticks/second
     * @param lastEventNanos System.nanoTime() of the most recent tick (0 for first tick)
     */
    private record RateState(double smoothedRate, long lastEventNanos) {
    }
}
