package com.tradej.strategy.observability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-strategy observability counters. Tracks (strategy, event-type,
 * outcome) tuples. Stable metric names are used by Prometheus
 * ({@code strategy.signals.count{strategy="…",outcome="…"}}) and the
 * dashboard's Strategy Catalog page.
 *
 * <p>Implementation note: deliberately not a Spring bean. The single
 * global instance is held by the {@code composition} module and shared
 * across all sandboxes. Counters are cumulative; rates are derived
 * by the scraper from the snapshot.
 */
public final class StrategyMetrics {

    public enum Outcome { OK, TIMEOUT, ERROR }

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public void recordOk(String strategy, String eventType) {
        record(strategy, eventType, Outcome.OK);
    }

    public void recordTimeout(String strategy, String eventType) {
        record(strategy, eventType, Outcome.TIMEOUT);
    }

    public void recordError(String strategy, String eventType) {
        record(strategy, eventType, Outcome.ERROR);
    }

    private void record(String strategy, String eventType, Outcome outcome) {
        String key = strategy + "|" + eventType + "|" + outcome.name();
        counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    public long count(String strategy, String eventType, Outcome outcome) {
        String key = strategy + "|" + eventType + "|" + outcome.name();
        AtomicLong c = counters.get(key);
        return c == null ? 0L : c.get();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> out = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }
}
