package com.tradej.simulation;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.Side;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks simulated P&L across positions. Maintains per-symbol position state
 * (net quantity, average price, realized P&L, unrealized P&L) and exposes
 * aggregate snapshot as {@link PnlUpdatedEvent}.
 */
public final class PnLLedger {

    private final Map<String, Position> positions = new ConcurrentHashMap<>();
    private long realizedPnlPaisa;
    private long unrealizedPnlPaisa;

    /**
     * Apply a simulated fill to the ledger.
     *
     * @param fill      the trade fill
     * @param markLtp   the last traded price for mark-to-market (or 0 to use fill price)
     */
    public void applyFill(Trade fill, long markLtp) {
        Position pos = positions.computeIfAbsent(fill.symbol(), s -> new Position(fill.symbol()));
        pos.apply(fill);

        // Recompute realized P&L across all positions
        realizedPnlPaisa = positions.values().stream()
                .mapToLong(Position::realizedPnlPaisa)
                .sum();

        // Mark-to-market at the given price or fill price
        long markPrice = markLtp > 0L ? markLtp : fill.pricePaisa();
        markToMarket(markPrice, fill.symbol());
    }

    /**
     * Mark a position to market at the given price (updates unrealized P&L).
     */
    public void markToMarket(long pricePaisa, String symbol) {
        Position pos = positions.get(symbol);
        if (pos != null) {
            pos.mark(pricePaisa);
        }
        unrealizedPnlPaisa = positions.values().stream()
                .mapToLong(Position::unrealizedPnlPaisa)
                .sum();
    }

    /** Total realized P&L across all positions in paisa. */
    public long realizedPnlPaisa() { return realizedPnlPaisa; }

    /** Total unrealized (mark-to-market) P&L across all positions in paisa. */
    public long unrealizedPnlPaisa() { return unrealizedPnlPaisa; }

    /** Net total P&L (realized + unrealized) in paisa. */
    public long totalPnlPaisa() { return realizedPnlPaisa + unrealizedPnlPaisa; }

    /**
     * Produce a {@link PnlUpdatedEvent} snapshot of current P&L state.
     */
    public PnlUpdatedEvent snapshot(EventMetadata metadata) {
        long netExposure = positions.values().stream()
                .mapToLong(p -> Math.abs(p.netQuantity()) * Math.max(p.avgPricePaisa(), 0L))
                .sum();
        return new PnlUpdatedEvent(metadata, realizedPnlPaisa, unrealizedPnlPaisa, netExposure);
    }

    /**
     * Publish the current P&L snapshot via the given consumer.
     */
    public void publishSnapshot(EventMetadata metadata, Consumer<PnlUpdatedEvent> publisher) {
        publisher.accept(snapshot(metadata));
    }

    /**
     * Per-symbol position state.
     */
    static final class Position {
        private final String symbol;
        private long netQuantity;
        private long avgPricePaisa;
        private long realizedPnlPaisa;
        private long unrealizedPnlPaisa;

        Position(String symbol) {
            this.symbol = symbol;
        }

        void apply(Trade fill) {
            long delta = fill.side() == Side.BUY ? fill.quantity() : -fill.quantity();
            long fillPrice = fill.pricePaisa();

            if (netQuantity == 0L) {
                // Opening a new position
                netQuantity = delta;
                avgPricePaisa = fillPrice;
                return;
            }

            if (Math.signum(netQuantity) == Math.signum(delta)) {
                // Adding to existing position (same direction)
                long totalCost = Math.abs(netQuantity) * avgPricePaisa + fill.quantity() * fillPrice;
                netQuantity += delta;
                avgPricePaisa = netQuantity == 0L ? 0L : totalCost / Math.abs(netQuantity);
            } else {
                // Reducing or reversing position (opposite direction)
                long closedQty = Math.min(Math.abs(netQuantity), fill.quantity());
                long pnl = netQuantity > 0L
                        ? (fillPrice - avgPricePaisa)
                        : (avgPricePaisa - fillPrice);
                realizedPnlPaisa += closedQty * pnl;
                netQuantity += delta;
                if (netQuantity == 0L) {
                    avgPricePaisa = 0L;
                } else if (Math.signum(netQuantity) != Math.signum(delta)) {
                    // Position reversed: new average price is the fill price
                    avgPricePaisa = fillPrice;
                }
            }
        }

        void mark(long pricePaisa) {
            if (netQuantity == 0L) {
                unrealizedPnlPaisa = 0L;
                return;
            }
            long pnl = netQuantity > 0L
                    ? (pricePaisa - avgPricePaisa)
                    : (avgPricePaisa - pricePaisa);
            unrealizedPnlPaisa = Math.abs(netQuantity) * pnl;
        }

        long realizedPnlPaisa() { return realizedPnlPaisa; }
        long unrealizedPnlPaisa() { return unrealizedPnlPaisa; }
        long netQuantity() { return netQuantity; }
        long avgPricePaisa() { return avgPricePaisa; }
    }
}
