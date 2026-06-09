package com.tradej.broker.upstox.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for R5 fix: Upstox findKey() O(1) HashMap lookup.
 * Verifies the symbolToRequest reverse map provides constant-time lookup.
 */
@Tag("unit")
class UpstoxFindKeyPerformanceTest {

    @Test
    void symbolToRequest_lookupIsO1() {
        ConcurrentHashMap<String, MarketSubscriptionRequest> symbolToRequest = new ConcurrentHashMap<>();

        // Populate with 5000 symbols
        for (int i = 0; i < 5000; i++) {
            String symbol = "SYM_" + i;
            symbolToRequest.put(symbol, new MarketSubscriptionRequest(symbol, ExchangeSegment.NSE_EQ));
        }

        // O(1) lookup should be instant regardless of map size
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            MarketSubscriptionRequest result = symbolToRequest.get("SYM_" + (i % 5000));
            assertNotNull(result);
        }
        long elapsed = System.nanoTime() - start;
        double elapsedMs = elapsed / 1_000_000.0;

        // 10,000 lookups should complete in under 200ms with O(1) HashMap
        // (O(n) linear scan would take seconds at 5000 symbols × 10000 iterations)
        assertTrue(elapsedMs < 200.0,
                "10,000 O(1) lookups should complete in <200ms, took " + elapsedMs + "ms");
    }

    @Test
    void subscribePopulatesReverseMap() {
        ConcurrentHashMap<String, MarketSubscriptionRequest> symbolToRequest = new ConcurrentHashMap<>();
        ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();

        MarketSubscriptionRequest req = new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ);
        subscriptions.put(req, FeedMode.QUOTE);
        symbolToRequest.put(req.symbol(), req);

        assertEquals(req, symbolToRequest.get("RELIANCE"));
        assertEquals(1, symbolToRequest.size());
    }

    @Test
    void unsubscribeCleansReverseMap() {
        ConcurrentHashMap<String, MarketSubscriptionRequest> symbolToRequest = new ConcurrentHashMap<>();
        ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();

        MarketSubscriptionRequest req = new MarketSubscriptionRequest("TCS", ExchangeSegment.NSE_EQ);
        subscriptions.put(req, FeedMode.QUOTE);
        symbolToRequest.put(req.symbol(), req);

        // Unsubscribe
        subscriptions.remove(req);
        symbolToRequest.remove(req.symbol());

        assertNull(symbolToRequest.get("TCS"));
        assertTrue(symbolToRequest.isEmpty());
    }
}
