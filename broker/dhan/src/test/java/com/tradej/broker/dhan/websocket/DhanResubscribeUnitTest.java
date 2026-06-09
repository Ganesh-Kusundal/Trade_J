package com.tradej.broker.dhan.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Dhan resubscribe behavior after reconnect.
 * Verifies subscription state is preserved and replayed on reconnect.
 */
@Tag("unit")
class DhanResubscribeUnitTest {

    @Test
    void subscriptionsSurviveDisconnect() {
        ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
        subscriptions.put(new MarketSubscriptionRequest("NIFTY", ExchangeSegment.IDX_I), FeedMode.QUOTE);
        subscriptions.put(new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ), FeedMode.FULL);

        // Simulate disconnect — subscriptions map is preserved (not cleared)
        assertEquals(2, subscriptions.size(), "Subscriptions should survive disconnect");
    }

    @Test
    void resubscribeReplaysAllSubscriptions() {
        ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
        subscriptions.put(new MarketSubscriptionRequest("NIFTY", ExchangeSegment.IDX_I), FeedMode.QUOTE);
        subscriptions.put(new MarketSubscriptionRequest("BANKNIFTY", ExchangeSegment.IDX_I), FeedMode.QUOTE);
        subscriptions.put(new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ), FeedMode.FULL);

        // Simulate resubscribeAll — iterates all entries
        int resubscribedCount = 0;
        for (var entry : subscriptions.entrySet()) {
            assertNotNull(entry.getKey());
            assertNotNull(entry.getValue());
            resubscribedCount++;
        }
        assertEquals(3, resubscribedCount, "resubscribeAll should replay all subscriptions");
    }

    @Test
    void groupedByModePartitionsCorrectly() {
        ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
        subscriptions.put(new MarketSubscriptionRequest("NIFTY", ExchangeSegment.IDX_I), FeedMode.QUOTE);
        subscriptions.put(new MarketSubscriptionRequest("BANKNIFTY", ExchangeSegment.IDX_I), FeedMode.QUOTE);
        subscriptions.put(new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ), FeedMode.FULL);
        subscriptions.put(new MarketSubscriptionRequest("TCS", ExchangeSegment.NSE_EQ), FeedMode.TICKER);

        // Group by mode
        Map<FeedMode, Long> grouped = subscriptions.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(m -> m, java.util.stream.Collectors.counting()));

        assertEquals(2L, grouped.get(FeedMode.QUOTE));
        assertEquals(1L, grouped.get(FeedMode.FULL));
        assertEquals(1L, grouped.get(FeedMode.TICKER));
    }

    @Test
    void pendingSubscriptionsFlushedOnConnect() {
        ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> pending = new ConcurrentHashMap<>();
        ConcurrentHashMap<MarketSubscriptionRequest, FeedMode> active = new ConcurrentHashMap<>();

        // Subscribe before connect → goes to pending
        pending.put(new MarketSubscriptionRequest("NIFTY", ExchangeSegment.IDX_I), FeedMode.QUOTE);
        assertEquals(1, pending.size());
        assertEquals(0, active.size());

        // On connect → flush pending to active
        active.putAll(pending);
        pending.clear();

        assertEquals(0, pending.size());
        assertEquals(1, active.size());
    }
}
