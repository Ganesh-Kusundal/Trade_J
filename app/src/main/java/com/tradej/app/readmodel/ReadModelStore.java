package com.tradej.app.readmodel;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory CQRS read model for operator UI, SSE, and gateway fan-out.
 */
public final class ReadModelStore {

    public record OrderView(String orderId, String symbol, String status, long quantity) {
    }

    public record PositionView(String symbol, long netQuantity, long avgPricePaisa) {
    }

    public record TickView(String symbol, long ltpPaisa, long exchangeTimestampMs) {
    }

    public record DepthView(String symbol, List<DepthLevel> bids, List<DepthLevel> asks, long exchangeTimestampMs) {
    }

    public record CandleView(String symbol, String interval, long closePaisa, long volume, boolean closed) {
    }

    public record SignalView(String signalId, String symbol, String side, String strategy) {
    }

    public record PnlView(long realizedPnlPaisa, long unrealizedPnlPaisa, long netExposurePaisa) {
    }

    public record ScanHitView(String symbol, String exchangeSegment, double score, List<String> reasons) {
    }

    public record ScanResultView(String runId, String profileId, int hitCount, List<ScanHitView> hits) {
    }

    public record ReadModelSnapshot(
            List<OrderView> orders,
            List<PositionView> positions,
            List<TickView> ticks,
            List<DepthView> depths,
            List<CandleView> candles,
            List<SignalView> signals,
            PnlView pnl,
            ScanResultView latestScan,
            long version
    ) {
    }

    private final Map<String, OrderView> orders = new ConcurrentHashMap<>();
    private final Map<String, PositionView> positions = new ConcurrentHashMap<>();
    private final Map<String, TickView> ticks = new ConcurrentHashMap<>();
    private final Map<String, DepthView> depths = new ConcurrentHashMap<>();
    private final Map<String, CandleView> candles = new ConcurrentHashMap<>();
    private final Map<String, SignalView> signals = new ConcurrentHashMap<>();
    private volatile PnlView pnl = new PnlView(0L, 0L, 0L);
    private volatile ScanResultView latestScan = null;
    // Tracks active trade IDs to guard against out-of-order TradeClosed.
    private final Set<String> activeTradeIds = ConcurrentHashMap.newKeySet();
    private final List<Consumer<ReadModelSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong version = new AtomicLong();

    public void onDomainEvent(DomainEvent event) {
        switch (event) {
            case OrderAccepted accepted -> putOrder(accepted.order());
            case OrderRejected rejected -> putOrder(rejected.order());
            case OrderFilled filled -> putOrder(filled.order());
            case TradeOpened opened -> {
                activeTradeIds.add(opened.tradeId());
                positions.merge(
                        opened.symbol(),
                        new PositionView(opened.symbol(), opened.size(), opened.entryPricePaisa()),
                        (existing, incoming) -> new PositionView(
                                opened.symbol(),
                                existing.netQuantity() + incoming.netQuantity(),
                                incoming.avgPricePaisa()
                        )
                );
            }
            case TradeClosed closed -> {
                if (activeTradeIds.remove(closed.tradeId())) {
            positions.computeIfPresent(closed.symbol(), (sym, existing) -> {
                long remaining = existing.netQuantity() - closed.size();
                return remaining <= 0 ? null : new PositionView(sym, remaining, existing.avgPricePaisa());
            });
                }
            }
            case TickReceived tick -> ticks.put(tick.symbol(), new TickView(
                    tick.symbol(), tick.ltpPaisa(), tick.exchangeTimestampMs()));
            case MarketTickEvent tick -> ticks.put(tick.symbol(), new TickView(
                    tick.symbol(), tick.ltpPaisa(), tick.exchangeTimestampEpochMs()));
            case DepthUpdateEvent depth -> depths.put(depth.symbol(), new DepthView(
                    depth.symbol(), depth.bids(), depth.asks(), depth.exchangeTimestampMs()));
            case CandleDeveloping developing -> putCandle(developing.candle(), false);
            case CandleClosed closed -> putCandle(closed.candle(), true);
            case SignalGenerated signal -> signals.put(signal.signalId(), new SignalView(
                    signal.signalId(), signal.symbol(), signal.side().name(), signal.setup()));
            case PnlUpdatedEvent updated -> pnl = new PnlView(
                    updated.realizedPnlPaisa(), updated.unrealizedPnlPaisa(), updated.netExposurePaisa());
            case ScanResultsPublished scan -> latestScan = new ScanResultView(
                    scan.runId(),
                    scan.profileId(),
                    scan.hitCount(),
                    scan.hits().stream()
                            .map(h -> new ScanHitView(h.symbol(), h.exchangeSegment(), h.score(), h.reasons()))
                            .toList()
            );
            default -> { }
        }
        version.incrementAndGet();
        notifyListeners();
    }

    private void putOrder(Order order) {
        orders.put(order.orderId(), new OrderView(
                order.orderId(),
                order.symbol(),
                order.status().name(),
                order.quantity()
        ));
    }

    private void putCandle(Candle candle, boolean closed) {
        String key = candle.symbol() + ":" + candle.interval();
        candles.put(key, new CandleView(
                candle.symbol(),
                candle.interval(),
                candle.closePaisa(),
                candle.volume(),
                closed
        ));
    }

    public ReadModelSnapshot snapshot() {
        return new ReadModelSnapshot(
                List.copyOf(orders.values()),
                List.copyOf(positions.values()),
                List.copyOf(ticks.values()),
                List.copyOf(depths.values()),
                List.copyOf(candles.values()),
                List.copyOf(signals.values()),
                pnl,
                latestScan,
                version.get()
        );
    }

    // ── Replay state isolation (AD-02) ──

    /**
     * Captures a deep-ish snapshot of all mutable read-model state for replay isolation.
     * Note: listeners are not snapshotted — they are re-registered on restore.
     */
    public ReplaySnapshot replaySnapshot() {
        return new ReplaySnapshot(
                new ConcurrentHashMap<>(orders),
                new ConcurrentHashMap<>(positions),
                new ConcurrentHashMap<>(ticks),
                new ConcurrentHashMap<>(depths),
                new ConcurrentHashMap<>(candles),
                new ConcurrentHashMap<>(signals),
                pnl,
                latestScan,
                version.get(),
                Set.copyOf(activeTradeIds)
        );
    }

    /** Restores read-model state from a previously captured snapshot. */
    public void restore(ReplaySnapshot snapshot) {
        orders.clear();
        orders.putAll(snapshot.orders());
        positions.clear();
        positions.putAll(snapshot.positions());
        ticks.clear();
        ticks.putAll(snapshot.ticks());
        depths.clear();
        depths.putAll(snapshot.depths());
        candles.clear();
        candles.putAll(snapshot.candles());
        signals.clear();
        signals.putAll(snapshot.signals());
        pnl = snapshot.pnl();
        latestScan = snapshot.latestScan();
        version.set(snapshot.version());
        activeTradeIds.clear();
        activeTradeIds.addAll(snapshot.activeTradeIds());
        notifyListeners();
    }

    /** Immutable snapshot of all mutable read-model state for replay isolation. */
    public record ReplaySnapshot(
            Map<String, OrderView> orders,
            Map<String, PositionView> positions,
            Map<String, TickView> ticks,
            Map<String, DepthView> depths,
            Map<String, CandleView> candles,
            Map<String, SignalView> signals,
            PnlView pnl,
            ScanResultView latestScan,
            long version,
            Set<String> activeTradeIds
    ) {}

    public void subscribe(Consumer<ReadModelSnapshot> listener) {
        listeners.add(listener);
        listener.accept(snapshot());
    }

    private void notifyListeners() {
        ReadModelSnapshot snapshot = snapshot();
        for (Consumer<ReadModelSnapshot> listener : listeners) {
            listener.accept(snapshot);
        }
    }

    public List<OrderView> orders() {
        return Collections.unmodifiableList(new ArrayList<>(orders.values()));
    }

    public List<PositionView> positions() {
        return Collections.unmodifiableList(new ArrayList<>(positions.values()));
    }
}
