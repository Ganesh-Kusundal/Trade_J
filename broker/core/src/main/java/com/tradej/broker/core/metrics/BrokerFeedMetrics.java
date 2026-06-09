package com.tradej.broker.core.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class BrokerFeedMetrics {

    public static final BrokerFeedMetrics INSTANCE = new BrokerFeedMetrics();

    private final ConcurrentHashMap<String, AtomicLong> tickReceived = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicLong>> tickDropped = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> parseErrors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> reconnects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> activeSubscriptions = new ConcurrentHashMap<>();

    public void recordTickReceived(String broker) {
        tickReceived.computeIfAbsent(broker, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordTickDropped(String broker, String reason) {
        tickDropped.computeIfAbsent(broker, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(reason, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordParseError(String broker) {
        parseErrors.computeIfAbsent(broker, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordReconnect(String broker) {
        reconnects.computeIfAbsent(broker, k -> new AtomicLong()).incrementAndGet();
    }

    public long getTickReceivedCount(String broker) {
        AtomicLong counter = tickReceived.get(broker);
        return counter != null ? counter.get() : 0L;
    }

    public long getTickDroppedCount(String broker, String reason) {
        ConcurrentHashMap<String, AtomicLong> brokerDropped = tickDropped.get(broker);
        if (brokerDropped == null) return 0L;
        AtomicLong counter = brokerDropped.get(reason);
        return counter != null ? counter.get() : 0L;
    }

    public long getParseErrorCount(String broker) {
        AtomicLong counter = parseErrors.get(broker);
        return counter != null ? counter.get() : 0L;
    }

    public long getReconnectCount(String broker) {
        AtomicLong counter = reconnects.get(broker);
        return counter != null ? counter.get() : 0L;
    }

    public void setActiveSubscriptions(String broker, int count) {
        activeSubscriptions.computeIfAbsent(broker, k -> new AtomicInteger()).set(count);
    }

    public int getActiveSubscriptions(String broker) {
        AtomicInteger gauge = activeSubscriptions.get(broker);
        return gauge != null ? gauge.get() : 0;
    }
}
