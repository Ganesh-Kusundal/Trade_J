package com.tradej.strategy.testing;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.Side;
import com.tradej.strategy.api.GraphStrategyPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test harness that simplifies {@link GraphStrategyPlugin} testing by providing
 * factory methods for market events and assertion helpers for signal verification.
 * <p>
 * Uses deterministic, counter-based sequence IDs for reproducibility.
 * <p>
 * Usage:
 * <pre>{@code
 * var harness = new StrategyTestHarness();
 * var tick = harness.fireTick("RELIANCE", 250_000L);
 * var signal = harness.evaluatePlugin(myPlugin, tick);
 * harness.assertSignalProduced("RELIANCE", Side.BUY);
 * }</pre>
 */
public final class StrategyTestHarness {

    private final AtomicLong sequenceCounter = new AtomicLong(0L);
    private final AtomicLong monotonicCounter = new AtomicLong(0L);
    private final List<SignalGenerated> collectedSignals = new ArrayList<>();
    private long baseTimestampMs;

    public StrategyTestHarness() {
        this(System.currentTimeMillis());
    }

    public StrategyTestHarness(long baseTimestampMs) {
        this.baseTimestampMs = baseTimestampMs;
    }

    // -- Event factories --

    /**
     * Creates a {@link MarketTickEvent} with auto-generated metadata and default
     * quantity/volume values.
     *
     * @param symbol   the trading symbol
     * @param ltpPaisa last traded price in paisa
     * @return the constructed tick event
     */
    public MarketTickEvent fireTick(String symbol, long ltpPaisa) {
        return fireTick(symbol, ltpPaisa, 1L, 100L);
    }

    /**
     * Creates a {@link MarketTickEvent} with auto-generated metadata and the
     * specified quantity and volume.
     *
     * @param symbol   the trading symbol
     * @param ltpPaisa last traded price in paisa
     * @param qty      last traded quantity
     * @param volume   cumulative volume
     * @return the constructed tick event
     */
    public MarketTickEvent fireTick(String symbol, long ltpPaisa, long qty, long volume) {
        long seq = sequenceCounter.incrementAndGet();
        long mono = monotonicCounter.addAndGet(1_000_000L);
        long ts = baseTimestampMs + seq;
        EventMetadata metadata = new EventMetadata(
                "harness-tick-" + symbol + "-" + seq, ts, mono, seq, "", 1);
        return new MarketTickEvent(
                metadata,
                seq,
                symbol,
                ExchangeSegment.NSE_EQ,
                FeedMode.TICKER,
                ltpPaisa,
                qty,
                volume,
                ts,
                Optional.empty(),
                0L,
                0L
        );
    }

    /**
     * Creates a {@link CandleClosed} event for the given symbol, close price,
     * and interval string (e.g. "5m").
     *
     * @param symbol      the trading symbol
     * @param closePaisa  close price in paisa
     * @param interval    candle interval label (e.g. "1m", "5m", "15m")
     * @return the constructed candle-closed event
     */
    public CandleClosed fireCandleClosed(String symbol, long closePaisa, String interval) {
        long seq = sequenceCounter.incrementAndGet();
        long mono = monotonicCounter.addAndGet(1_000_000L);
        long ts = baseTimestampMs + seq;
        long startMs = ts;
        long endMs = ts + intervalToMillis(interval);
        EventMetadata metadata = new EventMetadata(
                "harness-candle-" + symbol + "-" + seq, ts, mono, seq, "", 1);
        Candle candle = new Candle(
                symbol, interval, startMs, endMs,
                closePaisa, closePaisa, closePaisa - 5_000L, closePaisa,
                1_000L, true);
        return new CandleClosed(metadata, candle);
    }

    // -- Plugin evaluation --

    /**
     * Feeds a single event to the plugin and returns the resulting signal.
     * If the plugin produces a signal it is also added to the collected signals list.
     *
     * @param plugin the strategy plugin under test
     * @param event  the domain event to evaluate
     * @return the signal, if any
     */
    public Optional<SignalGenerated> evaluatePlugin(GraphStrategyPlugin plugin, DomainEvent event) {
        Optional<SignalGenerated> result = plugin.onEvent(event);
        result.ifPresent(collectedSignals::add);
        return result;
    }

    /**
     * Feeds multiple events sequentially to the plugin and collects all signals
     * produced across all invocations.
     *
     * @param plugin the strategy plugin under test
     * @param events the events to feed in order
     * @return all signals produced across all events
     */
    public List<SignalGenerated> evaluatePlugin(GraphStrategyPlugin plugin, DomainEvent... events) {
        List<SignalGenerated> results = new ArrayList<>();
        for (DomainEvent event : events) {
            Optional<SignalGenerated> signal = plugin.onEvent(event);
            signal.ifPresent(s -> {
                collectedSignals.add(s);
                results.add(s);
            });
        }
        return results;
    }

    // -- Assertions --

    /**
     * Asserts that at least one collected signal matches the given symbol and side.
     *
     * @param symbol the expected symbol
     * @param side   the expected side (BUY/SELL/LONG/SHORT)
     * @throws AssertionError if no matching signal is found
     */
    public void assertSignalProduced(String symbol, Side side) {
        boolean found = collectedSignals.stream()
                .anyMatch(s -> s.symbol().equals(symbol) && s.side() == side);
        if (!found) {
            throw new AssertionError(
                    "Expected signal for symbol=" + symbol + " side=" + side
                            + " but none found. Collected signals: " + collectedSignals);
        }
    }

    /**
     * Asserts that no signals have been collected.
     *
     * @throws AssertionError if any signal is present
     */
    public void assertNoSignal() {
        if (!collectedSignals.isEmpty()) {
            throw new AssertionError(
                    "Expected no signals but found " + collectedSignals.size()
                            + ": " + collectedSignals);
        }
    }

    // -- Accessors --

    /**
     * Returns all signals collected across all {@code evaluatePlugin} calls
     * since the last {@link #clear()}.
     */
    public List<SignalGenerated> signals() {
        return List.copyOf(collectedSignals);
    }

    /**
     * Resets all internal state: clears collected signals and resets the
     * sequence counter.
     */
    public void clear() {
        collectedSignals.clear();
        sequenceCounter.set(0L);
        monotonicCounter.set(0L);
    }

    // -- Internal helpers --

    private static long intervalToMillis(String interval) {
        if (interval == null || interval.isEmpty()) {
            return 300_000L; // default 5m
        }
        try {
            String unit = interval.substring(interval.length() - 1).toLowerCase();
            long value = Long.parseLong(interval.substring(0, interval.length() - 1));
            return switch (unit) {
                case "s" -> value * 1_000L;
                case "m" -> value * 60_000L;
                case "h" -> value * 3_600_000L;
                case "d" -> value * 86_400_000L;
                default -> 300_000L;
            };
        } catch (NumberFormatException e) {
            return 300_000L;
        }
    }
}
