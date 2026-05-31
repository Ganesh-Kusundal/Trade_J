package com.tradej.strategy.example;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.value.Side;
import com.tradej.strategy.api.GraphStrategyPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example {@link GraphStrategyPlugin} that detects rapid tick-level price changes
 * and generates momentum signals.
 *
 * <p>This strategy monitors {@link MarketTickEvent}s and compares the latest
 * price against a rolling reference. If the price moves beyond a configured
 * threshold within a time window, it generates a BUY (price spike up) or
 * SELL (price drop) signal.
 *
 * <p>Demonstrates the tick-level strategy pattern: subscribing to
 * {@code MarketTickEvent}, maintaining per-symbol state, and producing
 * signals from sub-second price action.
 */
public final class TickPriceChangeStrategy implements GraphStrategyPlugin {

    private static final Logger log = LoggerFactory.getLogger(TickPriceChangeStrategy.class);

    private final String name;
    private final long thresholdPaisa;
    private final long cooldownMs;
    private final ConcurrentHashMap<String, TickState> symbolStates = new ConcurrentHashMap<>();

    /**
     * @param name           unique plugin name
     * @param thresholdPaisa minimum price change in paisa to trigger a signal
     * @param cooldownMs     minimum interval between signals for the same symbol
     */
    public TickPriceChangeStrategy(String name, long thresholdPaisa, long cooldownMs) {
        this.name = name;
        this.thresholdPaisa = thresholdPaisa;
        this.cooldownMs = cooldownMs;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Class<? extends DomainEvent>> subscribedEventTypes() {
        return List.of(MarketTickEvent.class);
    }

    @Override
    public Optional<SignalGenerated> onEvent(DomainEvent event) {
        if (!(event instanceof MarketTickEvent tick)) {
            return Optional.empty();
        }

        String symbol = tick.symbol();
        long ltp = tick.ltpPaisa();
        long exchangeTs = tick.exchangeTimestampEpochMs();
        long now = exchangeTs > 0 ? exchangeTs : System.currentTimeMillis();

        TickState state = symbolStates.compute(symbol, (key, existing) -> {
            if (existing == null) {
                return new TickState(ltp, now, 0L);
            }

            // Check cooldown
            if (now - existing.lastSignalMs < cooldownMs) {
                return existing;
            }

            long priceChange = ltp - existing.referencePrice;
            if (Math.abs(priceChange) < thresholdPaisa) {
                return existing;
            }

            // Price change exceeds threshold — update reference and record signal time
            return new TickState(ltp, now, now);
        });

        // If lastSignalMs was updated to now, this tick triggered a signal
        if (state.lastSignalMs == now) {
            Side side = ltp > state.referencePrice ? Side.BUY : Side.SELL;
            String signalId = UUID.randomUUID().toString();
            long entryPrice = ltp;

            Map<String, Object> attrs = Map.of(
                    "strategyName", name,
                    "triggerEvent", "MarketTickEvent",
                    "referencePrice", state.referencePrice,
                    "priceChange", Math.abs(ltp - state.referencePrice),
                    "thresholdPaisa", thresholdPaisa
            );

            SignalGenerated signal = new SignalGenerated(
                    EventMetadata.correlated(tick.correlationId(), tick.sequenceId()),
                    signalId,
                    symbol,
                    "",  // tick-level, no interval
                    side,
                    entryPrice,
                    side == Side.BUY ? entryPrice - thresholdPaisa * 2 : entryPrice + thresholdPaisa * 2,
                    side == Side.BUY ? entryPrice + thresholdPaisa * 4 : entryPrice - thresholdPaisa * 4,
                    side == Side.BUY ? "TICK_SPIKE_UP" : "TICK_SPIKE_DOWN",
                    Collections.unmodifiableMap(attrs)
            );

            log.info("Tick price change signal plugin={} symbol={} side={} changePaisa={}",
                    name, symbol, side, Math.abs(ltp - state.referencePrice));

            return Optional.of(signal);
        }

        return Optional.empty();
    }

    @Override
    public void onStart() {
        log.info("Tick price change strategy '{}' started (threshold={} paisa, cooldown={}ms)",
                name, thresholdPaisa, cooldownMs);
    }

    @Override
    public void onStop() {
        symbolStates.clear();
        log.info("Tick price change strategy '{}' stopped", name);
    }

    /**
     * Per-symbol tracking state.
     *
     * @param referencePrice the reference price (paisa) used for change detection
     * @param updatedAtMs    timestamp of the last reference price update
     * @param lastSignalMs   timestamp of the last signal emission (0 = none yet)
     */
    private record TickState(long referencePrice, long updatedAtMs, long lastSignalMs) {
    }
}
