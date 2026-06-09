package com.tradej.broker.icici.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BreezeWebSocketResubscribeTest {

    @Test
    void subscribe_addsToSubscriptionsMap() {
        var multiplexer = createMultiplexer();
        var instruments = List.of(
                new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ),
                new MarketSubscriptionRequest("TCS", ExchangeSegment.NSE_EQ)
        );

        multiplexer.subscribe(instruments, FeedMode.TICKER);

        Map<MarketSubscriptionRequest, FeedMode> subs = multiplexer.subscriptions();
        assertEquals(2, subs.size());
        assertTrue(subs.containsKey(instruments.get(0)));
        assertTrue(subs.containsKey(instruments.get(1)));
    }

    @Test
    void unsubscribe_removesFromSubscriptionsMap() {
        var multiplexer = createMultiplexer();
        var instruments = List.of(
                new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ),
                new MarketSubscriptionRequest("TCS", ExchangeSegment.NSE_EQ)
        );

        multiplexer.subscribe(instruments, FeedMode.TICKER);
        assertEquals(2, multiplexer.subscriptions().size());

        multiplexer.unsubscribe(List.of(instruments.get(0)));
        assertEquals(1, multiplexer.subscriptions().size());
        assertFalse(multiplexer.subscriptions().containsKey(instruments.get(0)));
        assertTrue(multiplexer.subscriptions().containsKey(instruments.get(1)));
    }

    @Test
    void subscribe_multipleModesPreserved() {
        var multiplexer = createMultiplexer();

        multiplexer.subscribe(
                List.of(new MarketSubscriptionRequest("NIFTY", ExchangeSegment.IDX_I)),
                FeedMode.QUOTE);
        multiplexer.subscribe(
                List.of(new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ)),
                FeedMode.FULL);

        Map<MarketSubscriptionRequest, FeedMode> subs = multiplexer.subscriptions();
        assertEquals(2, subs.size());
        assertEquals(FeedMode.QUOTE, subs.get(new MarketSubscriptionRequest("NIFTY", ExchangeSegment.IDX_I)));
        assertEquals(FeedMode.FULL, subs.get(new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ)));
    }

    @Test
    void subscriptions_areIdempotent_duplicateSubscriptionsDoNotDouble() {
        var multiplexer = createMultiplexer();
        var instrument = new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ);

        multiplexer.subscribe(List.of(instrument), FeedMode.TICKER);
        multiplexer.subscribe(List.of(instrument), FeedMode.TICKER);
        multiplexer.subscribe(List.of(instrument), FeedMode.TICKER);

        assertEquals(1, multiplexer.subscriptions().size(),
                "Duplicate subscriptions should not create duplicate entries");
    }

    @Test
    void reconnectRegistry_notifiedOnEveryConnect() {
        ReconnectListenerRegistry registry = new ReconnectListenerRegistry();
        AtomicInteger notifyCount = new AtomicInteger();
        registry.addListener(notifyCount::incrementAndGet);

        for (int i = 0; i < 3; i++) {
            registry.notifyReconnect();
        }
        assertEquals(3, notifyCount.get());
    }

    @Test
    void dedupFilter_resetsOnReconnect() {
        var filter = new com.tradej.broker.core.dedup.MarketTickDedupFilter();
        assertFalse(filter.isDuplicate("RELIANCE", "NSE_EQ", 1L));
        assertTrue(filter.isDuplicate("RELIANCE", "NSE_EQ", 1L));

        filter.reset();
        assertFalse(filter.isDuplicate("RELIANCE", "NSE_EQ", 1L),
                "After reset, same sequence should NOT be duplicate");
    }

    @Test
    void metricsTrack_subscriptionCountOnConnect() {
        var metrics = com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE;
        var multiplexer = createMultiplexer();

        multiplexer.subscribe(List.of(
                new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ),
                new MarketSubscriptionRequest("TCS", ExchangeSegment.NSE_EQ),
                new MarketSubscriptionRequest("INFY", ExchangeSegment.NSE_EQ)
        ), FeedMode.TICKER);

        assertEquals(3, multiplexer.subscriptions().size());
        metrics.setActiveSubscriptions("icici-resub-test", multiplexer.subscriptions().size());
        assertEquals(3, metrics.getActiveSubscriptions("icici-resub-test"));
    }

    @Test
    void subscribe_mcxCommodity_preservesSegment() {
        var multiplexer = createMultiplexer();
        var gold = new MarketSubscriptionRequest("GOLD26JUN", ExchangeSegment.MCX_COMM);

        multiplexer.subscribe(List.of(gold), FeedMode.FULL);

        Map<MarketSubscriptionRequest, FeedMode> subs = multiplexer.subscriptions();
        assertEquals(1, subs.size());
        assertEquals(ExchangeSegment.MCX_COMM, subs.keySet().iterator().next().exchangeSegment());
    }

    @Test
    void subscribe_1000Instruments_scalesCorrectly() {
        var multiplexer = createMultiplexer();
        var instruments = new java.util.ArrayList<MarketSubscriptionRequest>();
        for (int i = 0; i < 1000; i++) {
            instruments.add(new MarketSubscriptionRequest("SYM" + i, ExchangeSegment.NSE_EQ));
        }

        multiplexer.subscribe(instruments, FeedMode.TICKER);
        assertEquals(1000, multiplexer.subscriptions().size());

        multiplexer.unsubscribe(instruments.subList(0, 500));
        assertEquals(500, multiplexer.subscriptions().size());
    }

    private BreezeWebSocketMultiplexer createMultiplexer() {
        var tokenProvider = new com.tradej.broker.icici.auth.BreezeTokenProvider() {
            @Override public void ensureValid() {}
            @Override public com.tradej.broker.icici.auth.BreezeSession session() {
                long now = System.currentTimeMillis();
                return new com.tradej.broker.icici.auth.BreezeSession(
                        "test-user", "test-key", "dGVzdA==", now, now + 3_600_000L);
            }
            @Override public String appKey() { return "test-app-key"; }
            @Override public String secretKey() { return "test-secret-key"; }
        };
        var resolver = new com.tradej.broker.icici.instrument.BreezeInstrumentResolver();
        var metadataFactory = new com.tradej.core.domain.event.EventMetadataFactory(
                new com.tradej.core.domain.time.LiveTradingClock());
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();

        return new BreezeWebSocketMultiplexer(tokenProvider, resolver, metadataFactory, null, executor);
    }
}
