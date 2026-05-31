package com.tradej.strategy.example;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.model.DepthLevel;
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
 * Example {@link GraphStrategyPlugin} that detects order book imbalance
 * from {@link DepthUpdateEvent}s and generates pressure signals.
 *
 * <p>This strategy monitors market depth updates and computes the bid/ask
 * volume imbalance. When the imbalance exceeds a configurable threshold,
 * it generates a signal reflecting the direction of order book pressure.
 *
 * <p>Demonstrates the depth-level strategy pattern: subscribing to
 * {@code DepthUpdateEvent}, maintaining per-symbol book state, and
 * producing signals from L2 order book data.
 */
public final class DepthImbalanceStrategy implements GraphStrategyPlugin {

    private static final Logger log = LoggerFactory.getLogger(DepthImbalanceStrategy.class);

    private static final double STOP_LOSS_FRACTION = 0.01;
    private static final double TAKE_PROFIT_FRACTION = 0.02;

    private final String name;
    private final double imbalanceThreshold;
    private final long cooldownMs;
    private final ConcurrentHashMap<String, DepthState> symbolStates = new ConcurrentHashMap<>();

    /**
     * @param name               unique plugin name
     * @param imbalanceThreshold minimum bid/ask volume ratio to trigger a signal
     *                           (e.g., 2.0 means bids are 2x asks for BUY, or vice versa for SELL)
     * @param cooldownMs         minimum interval between signals for the same symbol
     */
    public DepthImbalanceStrategy(String name, double imbalanceThreshold, long cooldownMs) {
        this.name = name;
        this.imbalanceThreshold = imbalanceThreshold;
        this.cooldownMs = cooldownMs;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Class<? extends DomainEvent>> subscribedEventTypes() {
        return List.of(DepthUpdateEvent.class);
    }

    @Override
    public Optional<SignalGenerated> onEvent(DomainEvent event) {
        if (!(event instanceof DepthUpdateEvent depth)) {
            return Optional.empty();
        }

        String symbol = depth.symbol();
        long now = depth.exchangeTimestampMs();
        if (now <= 0) {
            now = System.currentTimeMillis();
        }

        // Compute total bid and ask volume from the top levels
        long bidVolume = depth.bids().stream()
                .limit(5)
                .mapToLong(DepthLevel::quantity)
                .sum();
        long askVolume = depth.asks().stream()
                .limit(5)
                .mapToLong(DepthLevel::quantity)
                .sum();

        if (bidVolume <= 0 || askVolume <= 0) {
            return Optional.empty();
        }

        double ratio = (double) Math.max(bidVolume, askVolume) / Math.min(bidVolume, askVolume);
        if (ratio < imbalanceThreshold) {
            return Optional.empty();
        }

        // Check cooldown
        Optional<DepthState> existing = Optional.ofNullable(symbolStates.get(symbol));
        if (existing.isPresent() && now - existing.get().lastSignalMs < cooldownMs) {
            return Optional.empty();
        }

        boolean bidHeavy = bidVolume > askVolume;
        double imbalancePct = (ratio - 1.0) * 100.0;

        symbolStates.put(symbol, new DepthState(ratio, now));

        Side side = bidHeavy ? Side.BUY : Side.SELL;
        String signalId = UUID.randomUUID().toString();
        // Use the best bid or ask price as entry
        long entryPrice = bidHeavy
                ? depth.bids().getFirst().pricePaisa()
                : depth.asks().getFirst().pricePaisa();

        Map<String, Object> attrs = Map.of(
                "strategyName", name,
                "triggerEvent", "DepthUpdateEvent",
                "bidVolume", bidVolume,
                "askVolume", askVolume,
                "imbalanceRatio", ratio,
                "imbalancePct", Math.round(imbalancePct * 100.0) / 100.0,
                "levels", depth.levels()
        );

        SignalGenerated signal = new SignalGenerated(
                EventMetadata.correlated(depth.correlationId(), depth.sequenceId()),
                signalId,
                symbol,
                "",
                side,
                entryPrice,
                side == Side.BUY
                        ? entryPrice - (long) (entryPrice * STOP_LOSS_FRACTION)
                        : entryPrice + (long) (entryPrice * STOP_LOSS_FRACTION),
                side == Side.BUY
                        ? entryPrice + (long) (entryPrice * TAKE_PROFIT_FRACTION)
                        : entryPrice - (long) (entryPrice * TAKE_PROFIT_FRACTION),
                bidHeavy ? "DEPTH_BUYING_PRESSURE" : "DEPTH_SELLING_PRESSURE",
                Collections.unmodifiableMap(attrs)
        );

        log.info("Depth imbalance signal plugin={} symbol={} side={} ratio={} imbalancePct={}",
                name, symbol, side, String.format("%.2f", ratio), String.format("%.1f", imbalancePct));

        return Optional.of(signal);
    }

    @Override
    public void onStart() {
        log.info("Depth imbalance strategy '{}' started (threshold={}, cooldown={}ms)",
                name, imbalanceThreshold, cooldownMs);
    }

    @Override
    public void onStop() {
        symbolStates.clear();
        log.info("Depth imbalance strategy '{}' stopped", name);
    }

    /**
     * Per-symbol depth tracking state.
     *
     * @param lastRatio    the imbalance ratio at the last signal
     * @param lastSignalMs timestamp of the last signal emission
     */
    private record DepthState(double lastRatio, long lastSignalMs) {
    }
}
