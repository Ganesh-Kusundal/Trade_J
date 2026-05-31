package com.tradej.gateway.bridge;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bridges domain events from the event bus to the gateway WebSocket topic router.
 *
 * <p>Each domain event type is mapped to a {@link GatewayTopic} and serialized to JSON.
 *
 * <p>Includes an event-ID dedup cache to prevent duplicate broadcasts during replay
 * or rapid-fire duplicate delivery. Periodic pruning prevents unbounded cache growth
 * (fixes GB-01 — reduces unnecessary broadcast volume during high volatility).
 */
public final class GatewayEventBridge implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GatewayEventBridge.class);

    /** Maximum entries in the dedup cache before eviction kicks in. */
    private static final int MAX_DEDUP_ENTRIES = 200_000;

    /** Age-based eviction: run every N calls to avoid O(n) scan on every hot-path event. */
    private static final int EVICTION_INTERVAL = 1024;

    /** Events older than this TTL are pruned from the dedup cache. */
    private static final Duration DEDUP_TTL = Duration.ofSeconds(30);

    private final GatewayTopicRouter router;
    private final ObjectMapper objectMapper;
    private final InstrumentResolver instrumentResolver;

    // ── Event-ID dedup cache ──
    private final ConcurrentHashMap<String, Long> seenEventIds = new ConcurrentHashMap<>();
    private final AtomicLong bridgeCallCounter = new AtomicLong();
    private final AtomicLong dedupHitCount = new AtomicLong();
    private final AtomicLong eventCount = new AtomicLong();
    private final ScheduledExecutorService dedupPruner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gateway-dedup-pruner");
        t.setDaemon(true);
        return t;
    });

    public GatewayEventBridge(GatewayTopicRouter router, ObjectMapper objectMapper) {
        this(router, objectMapper, null);
    }

    public GatewayEventBridge(GatewayTopicRouter router, ObjectMapper objectMapper, InstrumentResolver instrumentResolver) {
        this.router = router;
        this.objectMapper = objectMapper;
        this.instrumentResolver = instrumentResolver;
        // Schedule periodic dedup pruning every 5 minutes
        dedupPruner.scheduleAtFixedRate(this::pruneDedupCache, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Register this bridge as a subscriber to specific domain events on the event bus.
     * Narrows subscriptions from {@code DomainEvent.class} to only the types the bridge
     * actually handles, reducing unnecessary dispatch overhead (ST-02).
     */
    public void register(EventBus eventBus) {
        eventBus.subscribe(MarketTickEvent.class, this::onDomainEvent);
        eventBus.subscribe(TickReceived.class, this::onDomainEvent);
        eventBus.subscribe(DepthUpdateEvent.class, this::onDomainEvent);
        eventBus.subscribe(CandleDeveloping.class, this::onDomainEvent);
        eventBus.subscribe(CandleClosed.class, this::onDomainEvent);
        eventBus.subscribe(OrderAccepted.class, this::onDomainEvent);
        eventBus.subscribe(OrderRejected.class, this::onDomainEvent);
        eventBus.subscribe(OrderFilled.class, this::onDomainEvent);
        eventBus.subscribe(TradeOpened.class, this::onDomainEvent);
        eventBus.subscribe(TradeClosed.class, this::onDomainEvent);
        eventBus.subscribe(SignalGenerated.class, this::onDomainEvent);
        eventBus.subscribe(ReplayTimeChangedEvent.class, this::onDomainEvent);
        eventBus.subscribe(PnlUpdatedEvent.class, this::onDomainEvent);
    }

    void onDomainEvent(DomainEvent event) {
        if (event == null) {
            log.warn("Null domain event received — skipping");
            return;
        }

        // Dedup: skip events with recently seen IDs (prevents duplicate broadcasts
        // during replay or when the same event arrives via multiple paths).
        if (isDuplicate(event)) {
            return;
        }

        try {
            switch (event) {
                case MarketTickEvent tick ->
                        router.publish(GatewayTopic.MARKET_TICK, writeJson(marketTickPayload(tick)));
                case TickReceived tick ->
                        router.publish(GatewayTopic.MARKET_TICK, writeJson(tickPayload(tick)));
                case DepthUpdateEvent depth ->
                        router.publish(GatewayTopic.MARKET_DEPTH, writeJson(depthPayload(depth)));
                case CandleDeveloping dev ->
                        router.publish(GatewayTopic.CANDLE_DEVELOPING, writeJson(candlePayload(dev.candle())));
                case CandleClosed closed ->
                        router.publish(GatewayTopic.CANDLE_CLOSED, writeJson(candlePayload(closed.candle())));
                case OrderAccepted accepted ->
                        router.publish(GatewayTopic.ORDER_UPDATE, writeJson(orderPayload(accepted)));
                case OrderRejected rejected ->
                        router.publish(GatewayTopic.ORDER_UPDATE, writeJson(orderPayload(rejected)));
                case OrderFilled filled ->
                        router.publish(GatewayTopic.ORDER_UPDATE, writeJson(orderPayload(filled)));
                case TradeOpened opened ->
                        router.publish(GatewayTopic.POSITION_UPDATE, writeJson(positionPayload(
                                opened.symbol(), opened.size(), opened.entryPricePaisa(), "OPEN")));
                case TradeClosed closed ->
                        router.publish(GatewayTopic.POSITION_UPDATE, writeJson(positionPayload(
                                closed.symbol(), 0L, 0L, "CLOSED")));
                case SignalGenerated signal ->
                        router.publish(GatewayTopic.STRATEGY_SIGNAL, writeJson(signalPayload(signal)));
                case ReplayTimeChangedEvent replay ->
                        router.publish(GatewayTopic.REPLAY_CONTROL, writeJson(replayPayload(replay)));
                case PnlUpdatedEvent pnl ->
                        router.publish(GatewayTopic.PNL_UPDATE, writeJson(pnlPayload(pnl)));
                default -> { }
            }
            eventCount.incrementAndGet();
        } catch (Exception e) {
            log.warn("Gateway bridge failed for {}: {}", event.getClass().getSimpleName(), e.getMessage());
        }
    }

    @Override
    public void close() {
        dedupPruner.shutdown();
        try {
            if (!dedupPruner.awaitTermination(2, TimeUnit.SECONDS)) {
                dedupPruner.shutdownNow();
            }
        } catch (InterruptedException e) {
            dedupPruner.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ── Metrics ──

    /** Number of events that were skipped due to dedup cache hit. */
    public long dedupHitCount() {
        return dedupHitCount.get();
    }

    /** Number of events successfully bridged to the router. */
    public long eventCount() {
        return eventCount.get();
    }

    /** Current size of the dedup cache. */
    public int dedupCacheSize() {
        return seenEventIds.size();
    }

    // ── Private helpers ──

    /**
     * Check if an event is a duplicate by its event ID.
     * When the cache reaches capacity, evicts only entries older than DEDUP_TTL
     * rather than clearing the entire cache.
     */
    private boolean isDuplicate(DomainEvent event) {
        // Age-based eviction: throttled to run every EVICTION_INTERVAL calls
        // to avoid O(n) iteration on every hot-path event.
        if (seenEventIds.size() >= MAX_DEDUP_ENTRIES
                && (bridgeCallCounter.incrementAndGet() & (EVICTION_INTERVAL - 1)) == 0) {
            long cutoff = System.currentTimeMillis() - DEDUP_TTL.toMillis();
            seenEventIds.values().removeIf(ts -> ts < cutoff);
        }
        Long previous = seenEventIds.putIfAbsent(event.eventId(), System.currentTimeMillis());
        if (previous != null) {
            dedupHitCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /** Periodic pruner to clean entries older than the TTL. */
    private void pruneDedupCache() {
        long cutoff = System.currentTimeMillis() - DEDUP_TTL.toMillis();
        int before = seenEventIds.size();
        seenEventIds.values().removeIf(ts -> ts < cutoff);
        int pruned = before - seenEventIds.size();
        if (pruned > 0 && log.isTraceEnabled()) {
            log.trace("Pruned {} stale entries from gateway dedup cache (size={})", pruned, seenEventIds.size());
        }
    }

    private byte[] writeJson(Map<String, Object> payload) throws com.fasterxml.jackson.core.JsonProcessingException {
        return objectMapper.writeValueAsBytes(payload);
    }

    // ── Payload builders ──

    private Map<String, Object> marketTickPayload(MarketTickEvent tick) {
        Map<String, Object> map = new LinkedHashMap<>();
        putSymbolFields(map, tick.symbol(), tick.segment());
        map.put("ltpPaisa", tick.ltpPaisa());
        map.put("lastTradeQuantity", tick.lastTradeQuantity());
        map.put("cumulativeVolume", tick.cumulativeVolume());
        map.put("exchangeTimestampMs", tick.exchangeTimestampEpochMs());
        map.put("segment", tick.segment().name());
        map.put("feedMode", tick.feedMode().name());
        map.put("sequence", tick.sequenceId());
        return map;
    }

    private Map<String, Object> tickPayload(TickReceived tick) {
        Map<String, Object> map = new LinkedHashMap<>();
        putSymbolFields(map, tick.symbol(), ExchangeSegment.NSE_EQ);
        map.put("ltpPaisa", tick.ltpPaisa());
        map.put("lastTradeQuantity", tick.lastTradeQuantity());
        map.put("cumulativeVolume", tick.cumulativeVolume());
        map.put("exchangeTimestampMs", tick.exchangeTimestampMs());
        map.put("sequence", tick.sequenceId());
        return map;
    }

    private Map<String, Object> depthPayload(DepthUpdateEvent depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        putSymbolFields(map, depth.symbol(), depth.segment());
        map.put("segment", depth.segment().name());
        map.put("levels", depth.levels());
        map.put("exchangeTimestampMs", depth.exchangeTimestampMs());
        map.put("bids", depth.bids().stream().map(GatewayEventBridge::depthLevelToMap).toList());
        map.put("asks", depth.asks().stream().map(GatewayEventBridge::depthLevelToMap).toList());
        map.put("sequence", depth.sequenceId());
        return map;
    }

    private static Map<String, Object> depthLevelToMap(DepthLevel level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pricePaisa", level.pricePaisa());
        m.put("quantity", level.quantity());
        m.put("orders", level.orderCount());
        return m;
    }

    private Map<String, Object> candlePayload(Candle candle) {
        Map<String, Object> map = new LinkedHashMap<>();
        String canonical = canonicalSymbol(candle.symbol(), ExchangeSegment.NSE_EQ);
        map.put("symbol", canonical);
        map.put("canonicalSymbol", canonical);
        map.put("interval", candle.interval());
        map.put("startTimeMs", candle.startTimeMs());
        map.put("endTimeMs", candle.endTimeMs());
        map.put("openPaisa", candle.openPaisa());
        map.put("highPaisa", candle.highPaisa());
        map.put("lowPaisa", candle.lowPaisa());
        map.put("closePaisa", candle.closePaisa());
        map.put("volume", candle.volume());
        return map;
    }

    private Map<String, Object> orderPayload(DomainEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        String type = event.getClass().getSimpleName();
        map.put("type", type);
        if (event instanceof OrderAccepted a) {
            map.put("orderId", a.order().orderId());
            putSymbolFields(map, a.order().symbol(), a.order().exchangeSegment());
            map.put("status", a.order().status().name());
            map.put("quantity", a.order().quantity());
            map.put("filledQuantity", a.order().filledQuantity());
            map.put("pricePaisa", a.order().pricePaisa());
            map.put("side", a.order().side().name());
        } else if (event instanceof OrderRejected r) {
            map.put("orderId", r.order().orderId());
            putSymbolFields(map, r.order().symbol(), r.order().exchangeSegment());
            map.put("status", r.order().status().name());
            map.put("reason", r.reason());
        } else if (event instanceof OrderFilled f) {
            map.put("orderId", f.order().orderId());
            putSymbolFields(map, f.order().symbol(), f.order().exchangeSegment());
            map.put("status", f.order().status().name());
            map.put("filledQuantity", f.order().filledQuantity());
            map.put("fillCount", f.fills().size());
            if (!f.fills().isEmpty()) {
                var firstFill = f.fills().getFirst();
                map.put("pricePaisa", firstFill.pricePaisa());
                map.put("tradeId", firstFill.tradeId());
            }
        }
        return map;
    }

    private Map<String, Object> positionPayload(String symbol, long size, long entryPricePaisa, String action) {
        Map<String, Object> map = new LinkedHashMap<>();
        putSymbolFields(map, symbol, ExchangeSegment.NSE_EQ);
        map.put("size", size);
        map.put("entryPricePaisa", entryPricePaisa);
        map.put("action", action);
        return map;
    }

    private Map<String, Object> signalPayload(SignalGenerated signal) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("signalId", signal.signalId());
        putSymbolFields(map, signal.symbol(), ExchangeSegment.NSE_EQ);
        map.put("side", signal.side().name());
        map.put("setup", signal.setup());
        return map;
    }

    private static Map<String, Object> replayPayload(ReplayTimeChangedEvent replay) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "REPLAY_TIME_CHANGED");
        map.put("currentTimeMs", replay.currentTimeMs());
        map.put("replaySpeedNanos", replay.replaySpeedNanos());
        return map;
    }

    private static Map<String, Object> pnlPayload(PnlUpdatedEvent pnl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("realizedPnlPaisa", pnl.realizedPnlPaisa());
        map.put("unrealizedPnlPaisa", pnl.unrealizedPnlPaisa());
        map.put("netExposurePaisa", pnl.netExposurePaisa());
        return map;
    }

    private void putSymbolFields(Map<String, Object> map, String symbol, ExchangeSegment segment) {
        String canonical = canonicalSymbol(symbol, segment);
        map.put("symbol", canonical);
        map.put("canonicalSymbol", canonical);
    }

    private String canonicalSymbol(String symbol, ExchangeSegment segment) {
        if (instrumentResolver == null || symbol == null || symbol.isBlank()) {
            return symbol;
        }
        try {
            return instrumentResolver.toCanonicalSymbol(symbol, segment);
        } catch (Exception ex) {
            return symbol;
        }
    }
}
