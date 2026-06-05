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

    // symbol -> aggregated position state
    private final ConcurrentHashMap<String, PositionState> positions = new ConcurrentHashMap<>();
    
    // Tracks active trade IDs to guard against out-of-order TradeClosed (defensive).
    private final ConcurrentHashMap.KeySetView<String, Boolean> activeTradeIds = ConcurrentHashMap.newKeySet();
    // Tracks individual active trade contributions to resolve positions on close.
    private final ConcurrentHashMap<String, TradeContribution> tradeContributions = new ConcurrentHashMap<>();
    // TradeClosed may arrive before TradeOpened on async buses — buffer until open is recorded.
    private final ConcurrentHashMap<String, TradeClosed> pendingCloses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap.KeySetView<String, Boolean> closedTradeIds = ConcurrentHashMap.newKeySet();
    // Cached net position snapshot to preserve snapshot/restore semantics.
    private final AtomicReference<StateSnapshot> snapshot = new AtomicReference<>();

    @Override
    public Map<String, Position> getPositions() {
        Map<String, Position> result = new java.util.HashMap<>();
        positions.forEach((symbol, state) -> {
            if (state.quantity() != 0) {
                result.put(symbol, new Position(symbol, state.quantity(), state.averagePricePaisa()));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    @Override
    public long getNetPosition(String symbol) {
        PositionState state = positions.get(symbol);
        return state != null ? state.quantity() : 0L;
    }

    public void onDomainEvent(DomainEvent event) {
        event.accept(new com.tradej.core.domain.event.DomainEventVisitor() {
            @Override
            public void visit(TradeOpened opened) {
                handleTradeOpened(opened);
            }

            @Override
            public void visit(TradeClosed closed) {
                handleTradeClosed(closed);
            }
        });
    }

    private void handleTradeOpened(TradeOpened opened) {
        String symbol = opened.symbol();
        long size = opened.size();
        long price = opened.entryPricePaisa();
        com.tradej.core.domain.value.Side side = opened.side();

        tradeContributions.put(opened.tradeId(), new TradeContribution(symbol, side, size, price));

        positions.compute(symbol, (s, current) -> {
            if (current == null) {
                return new PositionState(side.isBuySide() ? size : -size, price);
            }
            long oldQty = current.quantity();
            long oldAvg = current.averagePricePaisa();
            long tradeQty = side.isBuySide() ? size : -size;
            long newQty = oldQty + tradeQty;
            
            if (newQty == 0) {
                return new PositionState(0, 0);
            }
            
            // Institutional Weighted Average Cost Basis
            // Only update average price if increasing the position in the same direction
            long newAvg;
            if ((oldQty > 0 && tradeQty > 0) || (oldQty < 0 && tradeQty < 0)) {
                newAvg = (Math.abs(oldQty) * oldAvg + Math.abs(tradeQty) * price) / Math.abs(newQty);
            } else {
                // Position reduction or flip
                if (Math.signum(oldQty) == Math.signum(newQty)) {
                    // Same direction, just smaller qty - avg stays same
                    newAvg = oldAvg;
                } else {
                    // Flipped to opposite direction - new avg is the flip price
                    newAvg = price;
                }
            }
            return new PositionState(newQty, newAvg);
        });

        activeTradeIds.add(opened.tradeId());
        snapshot.set(null);
        
        log.debug("Position updated symbol={} side={} size={} price={} currentQty={} currentAvg={}",
                symbol, side, size, price, getNetPosition(symbol), 
                positions.get(symbol).averagePricePaisa());

        TradeClosed bufferedClose = pendingCloses.remove(opened.tradeId());
        if (bufferedClose != null) {
            closeContribution(opened.tradeId());
        }
    }

    private void handleTradeClosed(TradeClosed closed) {
        String tradeId = closed.tradeId();
        if (closedTradeIds.contains(tradeId)) {
            return;
        }
        TradeContribution contribution = tradeContributions.remove(tradeId);
        activeTradeIds.remove(tradeId);
        if (contribution == null) {
            pendingCloses.putIfAbsent(tradeId, closed);
            return;
        }
        applyContributionClose(tradeId, contribution);
    }

    private void closeContribution(String tradeId) {
        TradeContribution contribution = tradeContributions.remove(tradeId);
        activeTradeIds.remove(tradeId);
        if (contribution == null) return;
        applyContributionClose(tradeId, contribution);
    }

    private void applyContributionClose(String tradeId, TradeContribution contribution) {
        String symbol = contribution.symbol();
        long tradeQty = contribution.side().isBuySide() ? contribution.size() : -contribution.size();
        
        positions.computeIfPresent(symbol, (s, current) -> {
            long newQty = current.quantity() - tradeQty;
            if (newQty == 0) {
                return new PositionState(0, 0);
            }
            // Average price remains same on close/reduction
            return new PositionState(newQty, current.averagePricePaisa());
        });
        
        closedTradeIds.add(tradeId);
        snapshot.set(null);
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
                new ConcurrentHashMap<>(positions),
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
        positions.clear();
        activeTradeIds.clear();
        tradeContributions.clear();
        pendingCloses.clear();
        closedTradeIds.clear();
        positions.putAll(state.positions());
        activeTradeIds.addAll(state.activeTradeIds());
        tradeContributions.putAll(state.tradeContributions());
        snapshot.set(null);
    }

    public record PositionState(long quantity, long averagePricePaisa) {}

    public record TradeContribution(String symbol, com.tradej.core.domain.value.Side side, long size, long price) {}

    public record StateSnapshot(
            Map<String, PositionState> positions,
            Set<String> activeTradeIds,
            Map<String, TradeContribution> tradeContributions
    ) {
        public StateSnapshot {
            positions = Collections.unmodifiableMap(positions);
            activeTradeIds = Set.copyOf(activeTradeIds);
            tradeContributions = Collections.unmodifiableMap(tradeContributions);
        }
    }
}
