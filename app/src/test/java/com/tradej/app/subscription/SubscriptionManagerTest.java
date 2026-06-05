package com.tradej.app.subscription;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class SubscriptionManagerTest {

    @Test
    void snapshotMergesDesiredAndWireState() {
        RecordingWebSocket websocket = new RecordingWebSocket();
        SubscriptionCoordinator coordinator = new SubscriptionCoordinator(websocket, 100);
        SubscriptionManager manager = new SubscriptionManager(coordinator, websocket);

        MarketSubscriptionRequest request = new MarketSubscriptionRequest("SBIN", ExchangeSegment.NSE_EQ);
        manager.subscribe(List.of(request), FeedMode.TICKER);

        SubscriptionManager.SubscriptionSnapshot snapshot = manager.snapshot();
        assertTrue(snapshot.merged().get(FeedMode.TICKER).contains(request));
    }

    private static final class RecordingWebSocket implements WebSocketMultiplexer {
        private final java.util.concurrent.ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subs =
                new java.util.concurrent.ConcurrentHashMap<>();

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
            for (MarketSubscriptionRequest request : instruments) {
                subs.put(request, feedMode);
            }
        }

        @Override
        public void unsubscribe(java.util.Collection<MarketSubscriptionRequest> instruments) {
            for (MarketSubscriptionRequest request : instruments) {
                subs.remove(request);
            }
        }

        @Override
        public void onMarketData(com.tradej.broker.api.port.MarketDataListener listener) {
        }

        @Override
        public void onOrderUpdate(com.tradej.broker.api.port.OrderUpdateListener listener) {
        }

        @Override
        public Map<MarketSubscriptionRequest, FeedMode> subscriptions() {
            return Map.copyOf(subs);
        }
    }
}
