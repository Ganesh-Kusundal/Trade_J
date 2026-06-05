package com.tradej.app.subscription;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.WebSocketMultiplexer;
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

@Tag("unit")
class SubscriptionCoordinatorTest {

    @Test
    void replaceSubscriptionsReconcilesDiff() {
        RecordingMultiplexer websocket = new RecordingMultiplexer();
        SubscriptionCoordinator coordinator = new SubscriptionCoordinator(websocket, 50);
        MarketSubscriptionRequest sbin = new MarketSubscriptionRequest("SBIN", ExchangeSegment.NSE_EQ);
        MarketSubscriptionRequest reliance = new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ);

        coordinator.subscribe(List.of(sbin), FeedMode.TICKER);
        coordinator.replaceSubscriptions(Map.of(
                FeedMode.TICKER, Set.of(reliance)
        ));

        assertEquals(1, websocket.unsubscribes.size());
        assertEquals(List.of(sbin), websocket.unsubscribes.getFirst().requests());
        assertEquals(2, websocket.subscribes.size());
        assertEquals(List.of(reliance), websocket.subscribes.getLast().requests());
        assertEquals(1, coordinator.countsByFeedMode().get(FeedMode.TICKER));
    }

    @Test
    void reconcileAfterReconnectResubscribesSnapshot() {
        RecordingMultiplexer websocket = new RecordingMultiplexer();
        SubscriptionCoordinator coordinator = new SubscriptionCoordinator(websocket, 50);
        MarketSubscriptionRequest sbin = new MarketSubscriptionRequest("SBIN", ExchangeSegment.NSE_EQ);
        coordinator.subscribe(List.of(sbin), FeedMode.QUOTE);
        websocket.subscribes.clear();

        coordinator.reconcileAfterReconnect();

        assertEquals(1, websocket.subscribes.size());
        assertEquals(FeedMode.QUOTE, websocket.subscribes.getFirst().mode());
        assertEquals(List.of(sbin), websocket.subscribes.getFirst().requests());
    }

    private static final class RecordingMultiplexer implements WebSocketMultiplexer {
        private final List<WireCall> subscribes = new CopyOnWriteArrayList<>();
        private final List<WireCall> unsubscribes = new CopyOnWriteArrayList<>();

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
            unsubscribes.add(new WireCall(new ArrayList<>(instruments), null));
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
