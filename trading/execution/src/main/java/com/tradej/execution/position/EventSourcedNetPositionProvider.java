package com.tradej.execution.position;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.port.NetPositionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Derives net positions from trade lifecycle events and exposes a canonical
 * view keyed by plain symbol.
 *
 * <p>The resulting map is the single source of truth for {@code PositionRiskHandler}
 * pre-trade qualification under PE-02. It is recomputed from the stored
 * per-trade contributions whenever {@link #getNetPositions()} or
 * {@link #getNetPosition(String)} is invoked.
 */
public final class EventSourcedNetPositionProvider implements NetPositionProvider {

    private static final Logger log = LoggerFactory.getLogger(EventSourcedNetPositionProvider.class);

    // Tracks the long-side quantity opened per symbol.
    private final ConcurrentHashMap<String, Long> tradeLongs = new ConcurrentHashMap<>();
    // Tracks the short-side quantity opened per symbol.
    private final ConcurrentHashMap<String, Long> tradeShorts = new ConcurrentHashMap<>();
    // Tracks active trade IDs to guard against out-of-order TradeClosed (defensive).
    private final ConcurrentHashMap.KeySetView<String, Boolean> activeTradeIds = ConcurrentHashMap.newKeySet();
    // Tracks individual active trade contributions to resolve positions on close (fixes N-01).
    private final ConcurrentHashMap<String, TradeContribution> tradeContributions = new ConcurrentHashMap<>();
    // Cached net position snapshot to preserve snapshot/restore semantics.
    private final AtomicReference<StateSnapshot> snapshot = new AtomicReference<>();

    @Override
    public Map<String, Long> getNetPositions() {
        return Collections.unmodifiableMap(recompute());
    }

    @Override
    public long getNetPosition(String symbol) {
        return symbolNetPosition(symbol);
    }

    public void onDomainEvent(DomainEvent event) {
        if (event instanceof TradeOpened opened) {
            handleTradeOpened(opened);
        } else if (event instanceof TradeClosed closed) {
            handleTradeClosed(closed);
        }
    }

    private void handleTradeOpened(TradeOpened opened) {
        String symbol = opened.symbol();
        long size = opened.size();
        com.tradej.core.domain.value.Side side = opened.side();

        tradeContributions.put(opened.tradeId(), new TradeContribution(symbol, side, size));

        if (side.isBuySide()) {
            tradeLongs.merge(symbol, size, Long::sum);
        } else {
            tradeShorts.merge(symbol, size, Long::sum);
        }
        activeTradeIds.add(opened.tradeId());
        snapshot.set(null);
        log.debug(
                "Position opened symbol={} side={} size={} longs={} shorts={}",
                symbol,
                side,
                size,
                tradeLongs.getOrDefault(symbol, 0L),
                tradeShorts.getOrDefault(symbol, 0L));
    }

    private void handleTradeClosed(TradeClosed closed) {
        String tradeId = closed.tradeId();
        if (!activeTradeIds.remove(tradeId)) {
            log.warn("TradeClosed received for unknown tradeId={} symbol={} — ignoring", tradeId, closed.symbol());
            return;
        }
        TradeContribution contribution = tradeContributions.remove(tradeId);
        if (contribution == null) {
            log.warn("No active trade contribution tracked for tradeId={} symbol={} — closing fallback", tradeId, closed.symbol());
            return;
        }
        String symbol = contribution.symbol();
        long size = contribution.size();
        if (contribution.side().isBuySide()) {
            tradeLongs.computeIfPresent(symbol, (k, v) -> v > size ? v - size : null);
        } else {
            tradeShorts.computeIfPresent(symbol, (k, v) -> v > size ? v - size : null);
        }
        snapshot.set(null);
        log.debug("Position closed tradeId={} symbol={} size={}", closed.tradeId(), symbol, size);
    }

    private Map<String, Long> recompute() {
        ConcurrentHashMap<String, Long> combined = new ConcurrentHashMap<>();
        for (var entry : tradeLongs.entrySet()) {
            combined.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        for (var entry : tradeShorts.entrySet()) {
            combined.merge(entry.getKey(), -entry.getValue(), Long::sum);
        }
        return combined;
    }

    private long symbolNetPosition(String symbol) {
        long longQty = tradeLongs.getOrDefault(symbol, 0L);
        long shortQty = tradeShorts.getOrDefault(symbol, 0L);
        return longQty - shortQty;
    }

    /**
     * Captures a snapshot of all stored per-symbol position contributions.
     */
    public StateSnapshot snapshot() {
        StateSnapshot current = snapshot.get();
        if (current != null) {
            return current;
        }
        current = new StateSnapshot(
                new ConcurrentHashMap<>(tradeLongs),
                new ConcurrentHashMap<>(tradeShorts),
                Set.copyOf(activeTradeIds),
                new ConcurrentHashMap<>(tradeContributions));
        snapshot.compareAndSet(null, current);
        return current;
    }

    /**
     * Restores per-symbol position contributions from a previously captured snapshot.
     */
    public void restore(StateSnapshot state) {
        if (state == null) {
            return;
        }
        tradeLongs.clear();
        tradeShorts.clear();
        activeTradeIds.clear();
        tradeContributions.clear();
        tradeLongs.putAll(state.tradeLongs());
        tradeShorts.putAll(state.tradeShorts());
        activeTradeIds.addAll(state.activeTradeIds());
        tradeContributions.putAll(state.tradeContributions());
        snapshot.set(null);
        log.debug("Net position state restored from snapshot");
    }

    public record TradeContribution(String symbol, com.tradej.core.domain.value.Side side, long size) {}

    public record StateSnapshot(
            Map<String, Long> tradeLongs,
            Map<String, Long> tradeShorts,
            Set<String> activeTradeIds,
            Map<String, TradeContribution> tradeContributions
    ) {
        public StateSnapshot {
            tradeLongs = Collections.unmodifiableMap(tradeLongs);
            tradeShorts = Collections.unmodifiableMap(tradeShorts);
            activeTradeIds = Set.copyOf(activeTradeIds);
            tradeContributions = Collections.unmodifiableMap(tradeContributions);
        }
    }
}
