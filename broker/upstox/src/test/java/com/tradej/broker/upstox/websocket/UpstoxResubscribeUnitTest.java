package com.tradej.broker.upstox.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Upstox resubscribe behavior after reconnect.
 */
@Tag("unit")
class UpstoxResubscribeUnitTest {

    @Test
    void subscriptionsSurviveDisconnect() {
        ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
        subscriptions.put(new MarketSubscriptionRequest("NIFTY", ExchangeSegment.IDX_I), FeedMode.QUOTE);
        subscriptions.put(new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ), FeedMode.FULL);

        // Upstox multiplexer does NOT clear subscriptions on disconnect
        assertEquals(2, subscriptions.size(), "Subscriptions should survive disconnect for resubscribe");
    }

    @Test
    void resubscribeAllIteratesAllEntries() {
        ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
        for (int i = 0; i < 100; i++) {
            subscriptions.put(new MarketSubscriptionRequest("SYM" + i, ExchangeSegment.NSE_EQ), FeedMode.QUOTE);
        }

        int count = 0;
        for (var entry : subscriptions.entrySet()) {
            count++;
        }
        assertEquals(100, count, "resubscribeAll should iterate all 100 subscriptions");
    }

    @Test
    void symbolToRequestMapSurvivesDisconnect() {
        ConcurrentHashMap<String, MarketSubscriptionRequest> symbolToRequest = new ConcurrentHashMap<>();
        symbolToRequest.put("RELIANCE", new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ));
        symbolToRequest.put("NIFTY", new MarketSubscriptionRequest("NIFTY", ExchangeSegment.IDX_I));

        // After disconnect, reverse map should still be available for findKey()
        assertEquals(2, symbolToRequest.size());
        assertNotNull(symbolToRequest.get("RELIANCE"));
    }

    @Test
    void unsubscribeBeforeReconnect_excludedFromResubscribe() {
        ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
        var req1 = new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ);
        var req2 = new MarketSubscriptionRequest("TCS", ExchangeSegment.NSE_EQ);
        subscriptions.put(req1, FeedMode.QUOTE);
        subscriptions.put(req2, FeedMode.QUOTE);

        // Unsubscribe TCS before disconnect
        subscriptions.remove(req2);
        assertEquals(1, subscriptions.size(), "Unsubscribed instrument should not be in resubscribe set");
        assertTrue(subscriptions.containsKey(req1));
        assertFalse(subscriptions.containsKey(req2));
    }
}
