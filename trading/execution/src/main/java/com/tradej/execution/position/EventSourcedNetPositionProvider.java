package com.tradej.execution.position;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.port.NetPositionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
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

    // Tracks the long-side quantity opened for each trade.
    private final ConcurrentHashMap<String, Long> tradeLongs = new ConcurrentHashMap<>();
    // Tracks the short-side quantity opened for each trade.
    private final ConcurrentHashMap<String, Long> tradeShorts = new ConcurrentHashMap<>();
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
        switch (opened.side()) {
            case BUY -> tradeLongs.merge(symbol, size, Long::sum);
            case SELL -> tradeShorts.merge(symbol, size, Long::sum);
        }
        snapshot.set(null);
        log.debug(
                "Position opened symbol={} side={} size={} longs={} shorts={}",
                symbol,
                opened.side(),
                size,
                tradeLongs.getOrDefault(symbol, 0L),
                tradeShorts.getOrDefault(symbol, 0L));
    }

    private void handleTradeClosed(TradeClosed closed) {
        String symbol = closed.symbol();
        // Close the position recorded at open for this trade.
        tradeLongs.remove(symbol);
        tradeShorts.remove(symbol);
        snapshot.set(null);
        log.debug("Position closed tradeId={} symbol={}", closed.tradeId(), symbol);
    }

    private Map<String, Long> recompute() {
        ConcurrentHashMap<String, Long> combined = new ConcurrentHashMap<>();
        for (var entry : tradeLongs.entrySet()) {
            combined.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        for (var entry : tradeShorts.entrySet()) {
            combined.merge(entry.getKey(), entry.getValue(), (a, b) -> a - b);
        }
        return combined;
    }

    private long symbolNetPosition(String symbol) {
        long longQty = tradeLongs.getOrDefault(symbol, 0L);
        long shortQty = tradeShorts.getOrDefault(symbol, 0L);
        return longQty - shortQty;
    }

    /**
     * Captures a snapshot of all stored per-trade position contributions.
     */
    public StateSnapshot snapshot() {
        StateSnapshot current = snapshot.get();
        if (current != null) {
            return current;
        }
        current = new StateSnapshot(
                new ConcurrentHashMap<>(tradeLongs),
                new ConcurrentHashMap<>(tradeShorts));
        snapshot.compareAndSet(null, current);
        return current;
    }

    /**
     * Restores per-trade position contributions from a previously captured snapshot.
     */
    public void restore(StateSnapshot state) {
        if (state == null) {
            return;
        }
        tradeLongs.clear();
        tradeShorts.clear();
        tradeLongs.putAll(state.tradeLongs());
        tradeShorts.putAll(state.tradeShorts());
        snapshot.set(null);
        log.debug("Net position state restored from snapshot");
    }

    public record StateSnapshot(Map<String, Long> tradeLongs, Map<String, Long> tradeShorts) {
        public StateSnapshot {
            tradeLongs = Collections.unmodifiableMap(tradeLongs);
            tradeShorts = Collections.unmodifiableMap(tradeShorts);
        }
    }
}
