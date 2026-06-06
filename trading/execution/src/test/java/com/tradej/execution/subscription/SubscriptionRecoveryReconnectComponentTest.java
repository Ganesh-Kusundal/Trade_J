package com.tradej.execution.subscription;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("component")
class SubscriptionRecoveryReconnectComponentTest {

    @Test
    void registryNotifiesRecoveryAfterReconnect() {
        RecordingMultiplexer websocket = new RecordingMultiplexer();
        SubscriptionCoordinator coordinator = new SubscriptionCoordinator(websocket, 100);
        SubscriptionManager manager = new SubscriptionManager(coordinator, websocket);
        SubscriptionRecoveryManager recovery = new SubscriptionRecoveryManager(manager);
        ReconnectListenerRegistry registry = new ReconnectListenerRegistry();
        registry.addListener(recovery::recoverAfterReconnect);

        MarketSubscriptionRequest sbin = new MarketSubscriptionRequest("SBIN", ExchangeSegment.NSE_EQ);
        coordinator.subscribe(List.of(sbin), FeedMode.TICKER);
        websocket.subscribes.clear();

        registry.notifyReconnect();

        assertEquals(1, websocket.subscribes.size());
        assertEquals(FeedMode.TICKER, websocket.subscribes.getFirst().mode());
    }

    private static final class RecordingMultiplexer implements WebSocketMultiplexer {
        private final List<WireCall> subscribes = new CopyOnWriteArrayList<>();

        @Override
        public void connect() {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void subscribe(java.util.Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
            subscribes.add(new WireCall(new ArrayList<>(instruments), feedMode));
        }

        @Override
        public void unsubscribe(java.util.Collection<MarketSubscriptionRequest> instruments) {
        }

        @Override
        public void onMarketData(com.tradej.broker.api.port.MarketDataListener listener) {
        }

        @Override
        public void onOrderUpdate(com.tradej.broker.api.port.OrderUpdateListener listener) {
        }

        @Override
        public Map<MarketSubscriptionRequest, FeedMode> subscriptions() {
            return Map.of();
        }

        private record WireCall(List<MarketSubscriptionRequest> requests, FeedMode mode) {
        }
    }
}
