package com.tradej.broker.upstox.depth;

import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Upstox broker's market depth provider — backed by the shared
 * {@link UpstoxTwentyDepthWebSocketClient}.
 *
 * <p>Exposes:
 * <ul>
 *   <li>Active book keys for the depth WebSocket bridge</li>
 *   <li>Snapshot retrieval for the broker gateway REST surface</li>
 *   <li>A listener fan-out used by tests and analytics consumers</li>
 * </ul>
 */
public final class UpstoxMarketDepthProvider {

    private final UpstoxTwentyDepthWebSocketClient depthClient;
    private final Set<String> subscribedKeys = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<Consumer<DepthUpdateEvent>> depthListeners =
            new CopyOnWriteArrayList<>();

    public UpstoxMarketDepthProvider(UpstoxTwentyDepthWebSocketClient depthClient) {
        this.depthClient = depthClient;
    }

    public void subscribe(String symbol, ExchangeSegment segment) {
        subscribedKeys.add(bookKey(symbol, segment));
    }

    public void unsubscribe(String symbol, ExchangeSegment segment) {
        subscribedKeys.remove(bookKey(symbol, segment));
    }

    public boolean isSubscribed(String symbol, ExchangeSegment segment) {
        return subscribedKeys.contains(bookKey(symbol, segment));
    }

    public int subscriptionCount() {
        return subscribedKeys.size();
    }

    public void addDepthListener(Consumer<DepthUpdateEvent> listener) {
        depthListeners.add(listener);
    }

    public void removeDepthListener(Consumer<DepthUpdateEvent> listener) {
        depthListeners.remove(listener);
    }

    public MarketDepth snapshot(String symbol, ExchangeSegment segment, int levels) {
        var depth = depthClient.depthFor(
                new com.tradej.core.domain.model.InstrumentKey(symbol, segment));
        if (depth.isEmpty()) {
            return null;
        }
        MarketDepth full = depth.get();
        if (full.bids().size() <= levels && full.asks().size() <= levels) {
            return full;
        }
        return new MarketDepth(
                full.instrument(),
                full.bids().subList(0, Math.min(full.bids().size(), levels)),
                full.asks().subList(0, Math.min(full.asks().size(), levels)),
                levels,
                full.timestampMs());
    }

    public Map<String, ExchangeSegment> activeBooks() {
        return Map.of();
    }

    public int bookCount() {
        return depthClient.snapshotCount();
    }

    public static FeedMode depthFeedMode() {
        return FeedMode.DEPTH_20;
    }

    private static String bookKey(String symbol, ExchangeSegment segment) {
        return segment.name() + "::" + symbol;
    }
}
