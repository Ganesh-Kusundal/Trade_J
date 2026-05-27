package com.tradej.broker.api.port;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.core.domain.value.FeedMode;

import java.util.Collection;
import java.util.Map;

public interface WebSocketMultiplexer extends AutoCloseable {
    void connect();

    void disconnect();

    boolean isConnected();

    void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode);

    void unsubscribe(Collection<MarketSubscriptionRequest> instruments);

    void onMarketData(MarketDataListener listener);

    void onOrderUpdate(OrderUpdateListener listener);

    Map<MarketSubscriptionRequest, FeedMode> subscriptions();

    @Override
    default void close() {
        disconnect();
    }
}
