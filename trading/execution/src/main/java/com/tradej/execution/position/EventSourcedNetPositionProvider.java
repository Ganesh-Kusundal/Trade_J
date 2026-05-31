package com.tradej.execution.position;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.value.Side;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derives expected net positions from {@link TradeOpened} and {@link TradeClosed}
 * domain events for reconciliation pass 1.
 *
 * <p>Tracks per-trade signed contributions so that closing one trade on a symbol
 * correctly adjusts the net position when other trades on the same symbol remain open.
 */
public final class EventSourcedNetPositionProvider implements NetPositionProvider {

    private final Map<String, Long> netBySymbol = new ConcurrentHashMap<>();

    /** tradeId → signed quantity; used to correctly unwind multi-trade symbols on close. */
    private final Map<String, Long> tradeContributions = new ConcurrentHashMap<>();

    public void onDomainEvent(DomainEvent event) {
        switch (event) {
            case TradeOpened opened -> {
                long signed = signedQuantity(opened.side(), opened.size());
                tradeContributions.put(opened.tradeId(), signed);
                applyDelta(opened.symbol(), signed);
            }
            case TradeClosed closed -> {
                Long contribution = tradeContributions.remove(closed.tradeId());
                if (contribution != null) {
                    applyDelta(closed.symbol(), -contribution);
                }
            }
            default -> { }
        }
    }

    // ── Replay state isolation (AD-02) ──

    /** Captures a snapshot of all mutable position state for replay isolation. */
    public StateSnapshot snapshot() {
        return new StateSnapshot(
                new ConcurrentHashMap<>(netBySymbol),
                new ConcurrentHashMap<>(tradeContributions)
        );
    }

    /** Restores position state from a previously captured snapshot. */
    public void restore(StateSnapshot snapshot) {
        netBySymbol.clear();
        netBySymbol.putAll(snapshot.netBySymbol());
        tradeContributions.clear();
        tradeContributions.putAll(snapshot.tradeContributions());
    }

    /** Immutable snapshot of all mutable position state for replay isolation. */
    public record StateSnapshot(
            Map<String, Long> netBySymbol,
            Map<String, Long> tradeContributions
    ) {}

    @Override
    public Map<String, Long> getNetPositions() {
        return Collections.unmodifiableMap(netBySymbol);
    }

    private static long signedQuantity(Side side, long quantity) {
        return side == Side.SHORT || side == Side.SELL ? -quantity : quantity;
    }

    private void applyDelta(String symbol, long delta) {
        netBySymbol.merge(symbol, delta, Long::sum);
        netBySymbol.compute(symbol, (key, value) -> value == null || value == 0L ? null : value);
    }
}
