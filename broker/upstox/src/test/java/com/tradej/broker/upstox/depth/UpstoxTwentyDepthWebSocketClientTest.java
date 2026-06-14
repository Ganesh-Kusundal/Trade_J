package com.tradej.broker.upstox.depth;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
class UpstoxTwentyDepthWebSocketClientTest {

    private StubMultiplexer multiplexer;
    private UpstoxInstrumentResolver instrumentResolver;
    private UpstoxTwentyDepthWebSocketClient client;

    @BeforeEach
    void setUp() {
        multiplexer = new StubMultiplexer();
        instrumentResolver = mock(UpstoxInstrumentResolver.class);
        when(instrumentResolver.requireInstrumentKey(any(InstrumentKey.class)))
                .thenReturn("26000");
        client = new UpstoxTwentyDepthWebSocketClient(multiplexer, instrumentResolver);
    }

    @Test
    void subscribeRegistersDepth20Feed() {
        InstrumentKey key = new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ);
        client.subscribe(key);

        assertEquals(1, multiplexer.subscriptions.size());
        var entry = multiplexer.subscriptions.getFirst();
        assertEquals(FeedMode.DEPTH_20, entry.getValue());
        assertEquals("RELIANCE", entry.getKey().symbol());
        assertEquals(ExchangeSegment.NSE_EQ, entry.getKey().exchangeSegment());
    }

    @Test
    void parseProtobufDepthUpdate_20Levels() {
        InstrumentKey key = new InstrumentKey("TCS", ExchangeSegment.NSE_EQ);
        client.subscribe(key);

        List<DepthLevel> bids = new ArrayList<>();
        List<DepthLevel> asks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            bids.add(new DepthLevel((350000L - i * 100), (100L + i), (1 + i)));
            asks.add(new DepthLevel((350100L + i * 100), (200L + i), (2 + i)));
        }

        DepthUpdateEvent event = new DepthUpdateEvent(
                new EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock()).root(),
                "TCS",
                ExchangeSegment.NSE_EQ,
                bids,
                asks,
                20,
                System.currentTimeMillis());

        multiplexer.fireEvent(event);

        Optional<MarketDepth> depth = client.depthFor(key);
        assertTrue(depth.isPresent());
        MarketDepth snapshot = depth.get();
        assertEquals(20, snapshot.bids().size());
        assertEquals(20, snapshot.asks().size());
        assertEquals(20, snapshot.levels());
        assertEquals(350000L, snapshot.bids().getFirst().pricePaisa());
        assertEquals(350100L, snapshot.asks().getFirst().pricePaisa());
    }

    @Test
    void parseProtobufDepthUpdate_fewerLevelsStillValid() {
        InstrumentKey key = new InstrumentKey("INFY", ExchangeSegment.NSE_EQ);
        client.subscribe(key);

        List<DepthLevel> bids = List.of(
                new DepthLevel(150000L, 50L, 3),
                new DepthLevel(149900L, 30L, 2),
                new DepthLevel(149800L, 20L, 1));
        List<DepthLevel> asks = List.of(
                new DepthLevel(150100L, 40L, 2),
                new DepthLevel(150200L, 25L, 1));

        DepthUpdateEvent event = new DepthUpdateEvent(
                new EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock()).root(),
                "INFY",
                ExchangeSegment.NSE_EQ,
                bids,
                asks,
                3,
                System.currentTimeMillis());

        multiplexer.fireEvent(event);

        Optional<MarketDepth> depth = client.depthFor(key);
        assertTrue(depth.isPresent());
        MarketDepth snapshot = depth.get();
        assertEquals(3, snapshot.bids().size());
        assertEquals(2, snapshot.asks().size());
        assertEquals(3, snapshot.levels());
    }

    @Test
    void depthFor_returnsEmptyForUnsubscribedInstrument() {
        InstrumentKey key = new InstrumentKey("WIPRO", ExchangeSegment.NSE_EQ);
        assertFalse(client.depthFor(key).isPresent());
    }

    @Test
    void unsubscribe_removesDepth() {
        InstrumentKey key = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);
        client.subscribe(key);

        List<DepthLevel> bids = List.of(new DepthLevel(60000L, 100L, 5));
        List<DepthLevel> asks = List.of(new DepthLevel(60100L, 80L, 3));

        DepthUpdateEvent event = new DepthUpdateEvent(
                new EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock()).root(),
                "SBIN",
                ExchangeSegment.NSE_EQ,
                bids,
                asks,
                1,
                System.currentTimeMillis());

        multiplexer.fireEvent(event);
        assertTrue(client.depthFor(key).isPresent());

        client.unsubscribe(key);
        // Snapshot should still be there (unsubscribe just stops future updates)
        assertTrue(client.depthFor(key).isPresent());
    }

    private static class StubMultiplexer implements WebSocketMultiplexer {
        final List<Map.Entry<MarketSubscriptionRequest, FeedMode>> subscriptions = new CopyOnWriteArrayList<>();
        final List<MarketDataListener> listeners = new CopyOnWriteArrayList<>();
        private boolean connected = false;

        void fireEvent(com.tradej.core.domain.event.DomainEvent event) {
            for (MarketDataListener listener : listeners) {
                listener.onEvent(event);
            }
        }

        @Override
        public void connect() { connected = true; }

        @Override
        public void disconnect() { connected = false; }

        @Override
        public boolean isConnected() { return connected; }

        @Override
        public void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
            for (MarketSubscriptionRequest req : instruments) {
                subscriptions.add(Map.entry(req, feedMode));
            }
        }

        @Override
        public void unsubscribe(Collection<MarketSubscriptionRequest> instruments) {
            subscriptions.removeIf(e -> instruments.contains(e.getKey()));
        }

        @Override
        public void onMarketData(MarketDataListener listener) {
            listeners.add(listener);
        }

        @Override
        public void onOrderUpdate(com.tradej.broker.api.port.OrderUpdateListener listener) {}

        @Override
        public Map<MarketSubscriptionRequest, FeedMode> subscriptions() {
            return Map.ofEntries(subscriptions.toArray(new Map.Entry[0]));
        }

        @Override
        public void close() { disconnect(); }
    }
}
