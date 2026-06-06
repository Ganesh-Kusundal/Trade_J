package com.tradej.broker.dhan.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.depth.DhanTwentyDepthWebSocketClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.core.domain.value.FeedMode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages Dhan WebSocket subscription state, feed-key conversion, and depth client lifecycle.
 *
 * <p>Holds the canonical {@link MarketSubscriptionRequest} → {@link FeedMode} map and
 * provides utility methods used by {@link DhanWebSocketMultiplexer} when synchronizing
 * the actual WebSocket subscriptions under {@code transportLock}.
 *
 * <p>This class is <b>not</b> thread-safe for concurrent mutation of the subscription map
 * from multiple callers — the multiplexer is expected to serialize mutation calls through
 * its own synchronization. Concurrent reads via {@link #snapshot()} are safe.
 */
public final class DhanWebSocketSubscriptionManager {

    private final DhanInstrumentResolver resolver;
    private final Map<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
    private DhanTwentyDepthWebSocketClient depthClient;

    public DhanWebSocketSubscriptionManager(DhanInstrumentResolver resolver) {
        this.resolver = resolver;
    }

    // ---- Depth client ----

    /** Attach (or replace) the twenty-level depth WebSocket client. */
    public void setDepthClient(DhanTwentyDepthWebSocketClient client) {
        this.depthClient = client;
    }

    /** The currently attached depth client, or {@code null} if none. */
    public DhanTwentyDepthWebSocketClient getDepthClient() {
        return depthClient;
    }

    /** Whether a depth client is attached and connected. */
    public boolean isDepthConnected() {
        return depthClient != null && depthClient.isConnected();
    }

    // ---- Subscription state mutations ----

    /** Record a subscription for the given instrument and feed mode. */
    public void add(MarketSubscriptionRequest request, FeedMode mode) {
        subscriptions.put(request, mode);
    }

    /** Record subscriptions for multiple instruments with the same feed mode. */
    public void addAll(Collection<MarketSubscriptionRequest> requests, FeedMode mode) {
        requests.forEach(r -> subscriptions.put(r, mode));
    }

    /** Remove the given instruments from the subscription map. */
    public void removeAll(Collection<MarketSubscriptionRequest> requests) {
        requests.forEach(subscriptions::remove);
    }

    /** Remove all subscriptions. */
    public void clear() {
        subscriptions.clear();
    }

    /** The number of active subscriptions. */
    public int size() {
        return subscriptions.size();
    }

    /** Whether there are no active subscriptions. */
    public boolean isEmpty() {
        return subscriptions.isEmpty();
    }

    /** Whether any subscription uses {@link FeedMode#DEPTH_20}. */
    public boolean hasDepthSubscriptions() {
        return subscriptions.containsValue(FeedMode.DEPTH_20);
    }

    // ---- Snapshot / queries ----

    /** Immutable snapshot of current subscriptions. */
    public Map<MarketSubscriptionRequest, FeedMode> snapshot() {
        return Map.copyOf(subscriptions);
    }

    /** Get the feed mode for a specific instrument. */
    public FeedMode get(MarketSubscriptionRequest request) {
        return subscriptions.get(request);
    }

    // ---- Grouping utilities ----

    /** Group all subscriptions by feed mode. */
    public Map<FeedMode, List<MarketSubscriptionRequest>> groupedByMode() {
        return subscriptions.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
    }

    // ---- Feed-key conversion ----

    /** Convert a {@link MarketSubscriptionRequest} to a Dhan feed subscription key. */
    public DhanMarketFeedWebSocketClient.SubscriptionKey toFeedKey(MarketSubscriptionRequest request) {
        DhanInstrumentDefinition definition = resolver.requireDhanDefinition(
                request.symbol(), request.exchangeSegment());
        return new DhanMarketFeedWebSocketClient.SubscriptionKey(
                definition.exchangeSegment(), definition.securityId());
    }

    /** Convert multiple requests to feed keys. */
    public List<DhanMarketFeedWebSocketClient.SubscriptionKey> toFeedKeys(
            Collection<MarketSubscriptionRequest> requests) {
        return requests.stream().map(this::toFeedKey).toList();
    }

    /** Partition requests into depth subscriptions vs. regular market feed subscriptions. */
    public void partitionByDepth(
            Collection<MarketSubscriptionRequest> requests,
            List<MarketSubscriptionRequest> depthOut,
            List<MarketSubscriptionRequest> marketOut
    ) {
        for (MarketSubscriptionRequest request : requests) {
            if (subscriptions.get(request) == FeedMode.DEPTH_20) {
                depthOut.add(request);
            } else {
                marketOut.add(request);
            }
        }
    }
}
