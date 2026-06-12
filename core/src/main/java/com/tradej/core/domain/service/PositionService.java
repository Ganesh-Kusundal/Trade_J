package com.tradej.core.domain.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.port.NetPositionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canonical position service. Single source of truth for:
 * <ul>
 *   <li>net position per symbol (signed long)</li>
 *   <li>average entry price (per-symbol, weighted-average cost basis)</li>
 *   <li>realized PnL per symbol (cumulative, accumulated from {@link TradeClosed} events)</li>
 *   <li>unrealized PnL per symbol (computed on demand against a mark price via
 *       {@link com.tradej.core.domain.port.NetPositionProvider.Position#unrealizedPnlPaisa})</li>
 * </ul>
 *
 * <p><b>Event-sourced.</b> Consumes {@link TradeOpened} / {@link TradeClosed} via
 * {@link #onDomainEvent(DomainEvent)}. State is held in concurrent maps and is
 * safe to call from the event-dispatch thread.
 *
 * <p><b>Target of the P3 migration.</b> Existing consumers
 * ({@code com.tradej.execution.position.EventSourcedNetPositionProvider},
 * {@code com.tradej.strategy.portfolio.PortfolioEngine},
 * {@code com.tradej.execution.risk.PositionRiskHandler}) will be refactored to
 * delegate to this class in subsequent P3.x phases. Once all consumers are
 * migrated, {@code EventSourcedNetPositionProvider} will be deprecated and deleted.
 *
 * <p><b>Thread-safety.</b> All mutable state is in {@link ConcurrentHashMap}
 * (per-symbol). The position-update logic uses {@code compute} / {@code merge}
 * for atomic update under concurrent event delivery. Pending closes (out-of-order
 * events) are handled with a separate buffer map.
 */
public final class PositionService implements NetPositionProvider {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);

    private final ConcurrentHashMap<String, PositionState> positions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TradeContribution> openContributions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> openSizes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> realizedPnls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TradeClosed> pendingCloses = new ConcurrentHashMap<>();
    private final java.util.Set<String> closedTradeIds = ConcurrentHashMap.newKeySet();

    public PositionService() {
    }

    // ── NetPositionProvider port implementation ───────────────────────

    @Override
    public Map<String, Position> getPositions() {
        Map<String, Position> result = new HashMap<>();
        positions.forEach((symbol, state) -> {
            if (state.quantity() != 0) {
                result.put(symbol, new Position(symbol, state.quantity(), state.averagePricePaisa()));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    // ── PnL accessors ────────────────────────────────────────────────

    /**
     * Cumulative realized PnL for a symbol (sum of {@link TradeClosed#realizedPnlPaisa}
     * for all closed trades on that symbol).
     */
    public long getRealizedPnlPaisa(String symbol) {
        return realizedPnls.getOrDefault(symbol, 0L);
    }

    /**
     * Convenience: all-symbol realized PnL total.
     */
    public long getTotalRealizedPnlPaisa() {
        return realizedPnls.values().stream().mapToLong(Long::longValue).sum();
    }

    // ── Event-sourcing entry point ──────────────────────────────────

    /**
     * Apply a domain event to the position state. Idempotent for
     * {@link TradeClosed} (re-applying a close with the same {@code tradeId}
     * is a no-op).
     */
    public void onDomainEvent(DomainEvent event) {
        if (event instanceof TradeOpened opened) {
            handleTradeOpened(opened);
        } else if (event instanceof TradeClosed closed) {
            handleTradeClosed(closed);
        } else {
            log.debug("PositionService ignoring event type={}", event.getClass().getSimpleName());
        }
    }

    private void handleTradeOpened(TradeOpened opened) {
        String symbol = opened.symbol();
        long size = opened.size();
        long price = opened.entryPricePaisa();
        var side = opened.side();

        openContributions.put(opened.tradeId(),
                new TradeContribution(symbol, side, size, price));
        openSizes.put(opened.tradeId(), size);

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
            // Update average price only if increasing the position in the same direction.
            long newAvg;
            if ((oldQty > 0 && tradeQty > 0) || (oldQty < 0 && tradeQty < 0)) {
                newAvg = (Math.abs(oldQty) * oldAvg + Math.abs(tradeQty) * price) / Math.abs(newQty);
            } else {
                if (Math.signum(oldQty) == Math.signum(newQty)) {
                    // Reduction in same direction — average stays the same.
                    newAvg = oldAvg;
                } else {
                    // Flipped to opposite direction — new avg is the flip price.
                    newAvg = price;
                }
            }
            return new PositionState(newQty, newAvg);
        });

        log.debug("PositionService opened tradeId={} symbol={} side={} size={} price={} currentQty={}",
                opened.tradeId(), symbol, side, size, price, getNetPosition(symbol));

        // Out-of-order delivery: if a TradeClosed was buffered waiting for this open, drain it.
        TradeClosed bufferedClose = pendingCloses.remove(opened.tradeId());
        if (bufferedClose != null) {
            applyTradeClose(bufferedClose);
        }
    }

    private void handleTradeClosed(TradeClosed closed) {
        if (closedTradeIds.contains(closed.tradeId())) {
            return;
        }
        TradeContribution contribution = openContributions.get(closed.tradeId());
        if (contribution == null) {
            // TradeOpened not yet processed — buffer until it arrives.
            pendingCloses.putIfAbsent(closed.tradeId(), closed);
            return;
        }
        applyTradeClose(closed);
    }

    private void applyTradeClose(TradeClosed closed) {
        TradeContribution contribution = openContributions.get(closed.tradeId());
        closedTradeIds.add(closed.tradeId());

        if (contribution == null) {
            return;
        }

        // The TradeClosed event's `size` is the amount being closed (may be a
        // partial close of a larger TradeOpened). Use that, not the original
        // contribution size.
        long closedSize = closed.size();
        long tradeQty = contribution.side().isBuySide() ? closedSize : -closedSize;

        // Accumulate realized PnL from the event (authoritative source).
        realizedPnls.merge(contribution.symbol(), closed.realizedPnlPaisa(), Long::sum);

        // Update position: subtract the closed amount.
        positions.computeIfPresent(contribution.symbol(), (s, current) -> {
            long newQty = current.quantity() - tradeQty;
            if (newQty == 0) {
                return new PositionState(0, 0);
            }
            return new PositionState(newQty, current.averagePricePaisa());
        });

        // Decrement the open size; remove from open bookkeeping if fully closed.
        long newRemaining = openSizes.merge(closed.tradeId(), -closedSize, Long::sum);
        if (newRemaining <= 0) {
            openSizes.remove(closed.tradeId());
            openContributions.remove(closed.tradeId());
        }

        log.debug("PositionService closed tradeId={} symbol={} closedSize={} realizedPnl={} currentQty={}",
                closed.tradeId(), contribution.symbol(), closedSize, closed.realizedPnlPaisa(),
                getNetPosition(contribution.symbol()));
    }

    // ── Internal state records ──────────────────────────────────────

    public record PositionState(long quantity, long averagePricePaisa) {}

    public record TradeContribution(
            String symbol,
            com.tradej.core.domain.value.Side side,
            long size,
            long price) {
    }
}
