package com.tradej.broker.core.routing;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.OrderUpdateListener;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.core.domain.value.FeedMode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans out websocket subscriptions and multiplexes inbound events from all broker nodes.
 */
public final class FailoverWebSocketMultiplexer implements WebSocketMultiplexer {

    private final List<IBrokerConnection> connections;
    private final CopyOnWriteArrayList<MarketDataListener> marketDataListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderUpdateListener> orderUpdateListeners = new CopyOnWriteArrayList<>();

    private final ReconnectListenerRegistry reconnectRegistry;

    public FailoverWebSocketMultiplexer(List<IBrokerConnection> connections) {
        this(connections, null);
    }

    public FailoverWebSocketMultiplexer(List<IBrokerConnection> connections, ReconnectListenerRegistry reconnectRegistry) {
        if (connections == null || connections.isEmpty()) {
            throw new IllegalArgumentException("At least one broker connection is required");
        }
        this.connections = List.copyOf(connections);
        this.reconnectRegistry = reconnectRegistry;
        for (IBrokerConnection connection : this.connections) {
            connection.websocket().onMarketData(event -> {
                for (MarketDataListener listener : marketDataListeners) {
                    listener.onEvent(event);
                }
            });
            connection.websocket().onOrderUpdate(event -> {
                for (OrderUpdateListener listener : orderUpdateListeners) {
                    listener.onEvent(event);
                }
            });
        }
    }

    @Override
    public void connect() {
        for (IBrokerConnection connection : connections) {
            connection.websocket().connect();
        }
    }

    @Override
    public void disconnect() {
        for (IBrokerConnection connection : connections) {
            connection.websocket().disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        return connections.stream().anyMatch(connection -> connection.websocket().isConnected());
    }

    @Override
    public void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
        for (IBrokerConnection connection : connections) {
            connection.websocket().subscribe(instruments, feedMode);
        }
    }

    @Override
    public void unsubscribe(Collection<MarketSubscriptionRequest> instruments) {
        for (IBrokerConnection connection : connections) {
            connection.websocket().unsubscribe(instruments);
        }
    }

    @Override
    public void onMarketData(MarketDataListener listener) {
        marketDataListeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void onOrderUpdate(OrderUpdateListener listener) {
        orderUpdateListeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public Map<MarketSubscriptionRequest, FeedMode> subscriptions() {
        Map<MarketSubscriptionRequest, FeedMode> merged = new ConcurrentHashMap<>();
        for (IBrokerConnection connection : connections) {
            merged.putAll(connection.websocket().subscriptions());
        }
        return Map.copyOf(merged);
    }

    /**
     * Registers a listener that is notified when any downstream broker reconnects.
     */
    public void onReconnect(Runnable listener) {
        if (reconnectRegistry != null) {
            reconnectRegistry.addListener(listener);
        }
    }
}
