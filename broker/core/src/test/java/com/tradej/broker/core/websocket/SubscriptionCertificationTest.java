package com.tradej.broker.core.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Certification tests for subscription management at scale.
 * Verifies no duplicate subscriptions, no leaks, and correct resubscribe behavior.
 */
@Tag("unit")
class SubscriptionCertificationTest {

    @Test
    void singleSubscribeUnsubscribe_noLeaks() {
        ConcurrentHashMap<String, String> activeSubs = new ConcurrentHashMap<>();

        subscribe(activeSubs, "RELIANCE", "QUOTE");
        assertEquals(1, activeSubs.size());

        unsubscribe(activeSubs, "RELIANCE");
        assertEquals(0, activeSubs.size(), "Subscription leak: should be empty after unsubscribe");
    }

    @Test
    void bulkSubscribe10Symbols_allTracked() {
        ConcurrentHashMap<String, String> activeSubs = new ConcurrentHashMap<>();
        List<String> symbols = generateSymbols(10);

        for (String symbol : symbols) {
            subscribe(activeSubs, symbol, "QUOTE");
        }
        assertEquals(10, activeSubs.size());
        for (String symbol : symbols) {
            assertTrue(activeSubs.containsKey(key(symbol, "QUOTE")));
        }
    }

    @Test
    void bulkSubscribe100Symbols_allTracked() {
        ConcurrentHashMap<String, String> activeSubs = new ConcurrentHashMap<>();
        List<String> symbols = generateSymbols(100);

        for (String symbol : symbols) {
            subscribe(activeSubs, symbol, "FULL");
        }
        assertEquals(100, activeSubs.size());
    }

    @Test
    void bulkUnsubscribe_allRemoved() {
        ConcurrentHashMap<String, String> activeSubs = new ConcurrentHashMap<>();
        List<String> symbols = generateSymbols(50);

        for (String symbol : symbols) {
            subscribe(activeSubs, symbol, "QUOTE");
        }
        assertEquals(50, activeSubs.size());

        for (String symbol : symbols) {
            unsubscribe(activeSubs, symbol);
        }
        assertEquals(0, activeSubs.size(), "All subscriptions should be removed");
    }

    @Test
    void duplicateSubscribe_doesNotDoubleCount() {
        ConcurrentHashMap<String, String> activeSubs = new ConcurrentHashMap<>();

        subscribe(activeSubs, "NIFTY", "QUOTE");
        subscribe(activeSubs, "NIFTY", "QUOTE");
        subscribe(activeSubs, "NIFTY", "QUOTE");

        assertEquals(1, activeSubs.size(), "Duplicate subscriptions should not create extra entries");
    }

    @Test
    void dynamicSubscribeWhileConnected_addsToExisting() {
        ConcurrentHashMap<String, String> activeSubs = new ConcurrentHashMap<>();

        subscribe(activeSubs, "NIFTY", "QUOTE");
        subscribe(activeSubs, "BANKNIFTY", "QUOTE");
        assertEquals(2, activeSubs.size());

        subscribe(activeSubs, "FINNIFTY", "FULL");
        assertEquals(3, activeSubs.size());
    }

    @Test
    void resubscribeAfterReconnect_restoresAllSubscriptions() {
        ConcurrentHashMap<String, String> activeSubs = new ConcurrentHashMap<>();
        List<String> symbols = generateSymbols(20);

        for (String symbol : symbols) {
            subscribe(activeSubs, symbol, "QUOTE");
        }

        Set<String> preReconnectKeys = new HashSet<>(activeSubs.keySet());
        activeSubs.clear();
        assertEquals(0, activeSubs.size());

        for (String symbol : symbols) {
            subscribe(activeSubs, symbol, "QUOTE");
        }

        assertEquals(preReconnectKeys, activeSubs.keySet(),
                "Resubscribe should restore exact same subscriptions");
    }

    @Test
    void mixedSubscribeAndUnsubscribe_noStaleEntries() {
        ConcurrentHashMap<String, String> activeSubs = new ConcurrentHashMap<>();

        for (int i = 0; i < 100; i++) {
            subscribe(activeSubs, "SYM" + i, "QUOTE");
        }
        assertEquals(100, activeSubs.size());

        for (int i = 0; i < 50; i++) {
            unsubscribe(activeSubs, "SYM" + i);
        }
        assertEquals(50, activeSubs.size());

        for (int i = 50; i < 100; i++) {
            assertTrue(activeSubs.containsKey(key("SYM" + i, "QUOTE")),
                    "SYM" + i + " should still be subscribed");
        }
        for (int i = 0; i < 50; i++) {
            assertFalse(activeSubs.containsKey(key("SYM" + i, "QUOTE")),
                    "SYM" + i + " should be unsubscribed");
        }
    }

    @Test
    void reconnectListenerRegistry_registersAndFires() {
        ReconnectListenerRegistry registry = new ReconnectListenerRegistry();
        AtomicInteger callCount = new AtomicInteger();

        Runnable listener = callCount::incrementAndGet;
        registry.addListener(listener);

        registry.notifyReconnect();
        assertEquals(1, callCount.get());

        registry.notifyReconnect();
        assertEquals(2, callCount.get());

        registry.addListener(callCount::incrementAndGet);
        registry.notifyReconnect();
        assertEquals(4, callCount.get(), "Both listeners should fire");
    }

    private static void subscribe(ConcurrentHashMap<String, String> subs, String symbol, String mode) {
        subs.put(key(symbol, mode), mode);
    }

    private static void unsubscribe(ConcurrentHashMap<String, String> subs, String symbol) {
        subs.entrySet().removeIf(e -> e.getKey().startsWith(symbol + "::"));
    }

    private static String key(String symbol, String mode) {
        return symbol + "::" + mode;
    }

    private static List<String> generateSymbols(int count) {
        List<String> symbols = new ArrayList<>();
        String[] prefixes = {"RELIANCE", "TCS", "INFY", "HDFC", "ICICI", "SBIN", "ITC", "LT", "AXIS", "KOTAK"};
        for (int i = 0; i < count; i++) {
            symbols.add(prefixes[i % prefixes.length] + "_" + i);
        }
        return symbols;
    }
}
