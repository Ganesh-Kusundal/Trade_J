package com.tradej.strategy.example;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.id.IdGenerator;
import com.tradej.core.domain.id.UuidIdGenerator;
import com.tradej.strategy.api.GraphStrategyPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Textbook simple-moving-average crossover strategy.
 *
 * <p>Computes a fast (default 7) and slow (default 25) SMA on the close
 * price of incoming {@link CandleClosed} events. When the fast SMA
 * crosses above the slow SMA, emits a {@code BUY} signal; when it
 * crosses below, emits a {@code SELL} signal.
 *
 * <p>This is the canonical "first strategy a new quant writes" — small
 * enough to read in one minute, deterministic, and useful as a
 * regression test for the strategy SDK contract.
 *
 * <p>State is held per-symbol in a {@link ConcurrentHashMap}; the
 * rolling SMA is computed in a {@link Deque} capped at the slow period.
 * The strategy is side-effect-free beyond emitting signals and
 * maintains no other state.
 */
public class SmaCrossStrategy implements GraphStrategyPlugin {

    private static final Logger log = LoggerFactory.getLogger(SmaCrossStrategy.class);

    private final int fastPeriod;
    private final int slowPeriod;
    private final IdGenerator idGenerator;
    private final Map<String, Deque<Double>> closes = new ConcurrentHashMap<>();

    public SmaCrossStrategy() {
        this(7, 25);
    }

    public SmaCrossStrategy(int fastPeriod, int slowPeriod) {
        this(fastPeriod, slowPeriod, new UuidIdGenerator());
    }

    public SmaCrossStrategy(int fastPeriod, int slowPeriod, IdGenerator idGenerator) {
        if (fastPeriod < 1) throw new IllegalArgumentException("fastPeriod must be >= 1");
        if (slowPeriod <= fastPeriod) {
            throw new IllegalArgumentException("slowPeriod must be > fastPeriod");
        }
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.idGenerator = idGenerator != null ? idGenerator : new UuidIdGenerator();
    }

    @Override
    public String name() {
        return "sma-cross-" + fastPeriod + "-" + slowPeriod;
    }

    @Override
    public List<Class<? extends DomainEvent>> subscribedEventTypes() {
        return List.of(CandleClosed.class);
    }

    @Override
    public Optional<SignalGenerated> onEvent(DomainEvent event) {
        if (!(event instanceof CandleClosed closed)) return Optional.empty();
        String symbol = closed.candle().symbol();
        Deque<Double> window = closes.computeIfAbsent(symbol, k -> new ArrayDeque<>(slowPeriod + 1));
        synchronized (window) {
            window.addLast(closed.candle().closePaisa() / 100.0);
            if (window.size() > slowPeriod) {
                window.removeFirst();
            }
            if (window.size() < slowPeriod) {
                return Optional.empty();
            }
            double fast = average(window, fastPeriod);
            double slow = average(window, slowPeriod);
            long entryPaisa = closed.candle().closePaisa();
            long stopLossPaisa = (long) (entryPaisa * 0.02);
            long takeProfitPaisa = (long) (entryPaisa * 0.04);
            String reason = "fast=" + String.format("%.2f", fast) + " slow=" + String.format("%.2f", slow);
            if (fast > slow) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(),
                        idGenerator.generateSignalId(),
                        symbol,
                        closed.candle().interval(),
                        Side.BUY,
                        entryPaisa,
                        entryPaisa - stopLossPaisa,
                        entryPaisa + takeProfitPaisa,
                        "SMA_CROSS_UP",
                        Map.of("strength", (fast - slow) / slow, "note", reason)
                ));
            } else if (fast < slow) {
                return Optional.of(new SignalGenerated(
                        EventMetadata.root(),
                        idGenerator.generateSignalId(),
                        symbol,
                        closed.candle().interval(),
                        Side.SELL,
                        entryPaisa,
                        entryPaisa + stopLossPaisa,
                        entryPaisa - takeProfitPaisa,
                        "SMA_CROSS_DOWN",
                        Map.of("strength", (slow - fast) / slow, "note", reason)
                ));
            }
            return Optional.empty();
        }
    }

    private static double average(Deque<Double> window, int period) {
        int n = 0;
        double sum = 0.0;
        for (double d : window) {
            if (n == period) break;
            sum += d;
            n++;
        }
        return sum / period;
    }

    @Override
    public void onStop() {
        closes.clear();
        log.debug("SmaCrossStrategy stopped, state cleared");
    }

    /** Test-only — clears the per-symbol rolling windows. */
    void resetForTest() {
        closes.clear();
    }
}
