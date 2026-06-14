package com.tradej.brokergateway.simulation;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.OrderUpdateListener;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.value.FeedMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simulated WebSocket multiplexer for paper trading.
 * Tracks subscriptions but does not emit real market data events.
 * Can be used with SimulatedMarketDataProvider for polling-based paper trading.
 */
public final class SimulatedWebSocketMultiplexer implements WebSocketMultiplexer {

    private volatile boolean connected = false;
    private final Map<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<MarketDataListener> marketListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderUpdateListener> orderListeners = new CopyOnWriteArrayList<>();

    @Override public void connect() { connected = true; }
    @Override public void disconnect() { connected = false; }
    @Override public boolean isConnected() { return connected; }

    @Override
    public void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
        instruments.forEach(i -> subscriptions.put(i, feedMode));
    }

    @Override
    public void unsubscribe(Collection<MarketSubscriptionRequest> instruments) {
        instruments.forEach(subscriptions::remove);
    }

    @Override public void onMarketData(MarketDataListener listener) { marketListeners.add(listener); }
    @Override public void onOrderUpdate(OrderUpdateListener listener) { orderListeners.add(listener); }

    @Override
    public Map<MarketSubscriptionRequest, FeedMode> subscriptions() {
        return Map.copyOf(subscriptions);
    }

    /** Package-private accessor for the paper broker to publish lifecycle events. */
    public List<OrderUpdateListener> orderListeners() {
        return List.copyOf(orderListeners);
    }
}
