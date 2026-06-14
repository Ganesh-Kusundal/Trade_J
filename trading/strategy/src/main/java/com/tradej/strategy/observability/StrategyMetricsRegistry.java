package com.tradej.strategy.observability;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide aggregation of every {@link StrategyMetrics} that has
 * been registered. Strategies that don't share a {@code GraphStrategySandbox}
 * are still aggregated here so {@code /actuator/metrics} and the
 * {@code STRATEGY_METRICS} WS topic see a unified view.
 *
 * <p>Counters are added by calling {@link #register(String, String, StrategyMetrics.Outcome)}
 * (or {@link #registerAll(StrategyMetrics)}). The aggregator exposes
 * a flat map snapshot for the bus.
 */
public final class StrategyMetricsRegistry {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, StrategyMetrics> registeredSources = new ConcurrentHashMap<>();

    public void register(String sourceId, StrategyMetrics source) {
        if (source == null) return;
        registeredSources.put(sourceId, source);
    }

    public void unregister(String sourceId) {
        registeredSources.remove(sourceId);
    }

    public Map<String, Long> snapshot() {
        // First, sum the per-source snapshots.
        Map<String, Long> agg = new HashMap<>();
        for (StrategyMetrics src : registeredSources.values()) {
            for (var e : src.snapshot().entrySet()) {
                agg.merge(e.getKey(), e.getValue(), Long::sum);
            }
        }
        return agg;
    }

    /**
     * Pre-built copy returned by {@link #snapshot()} to keep callers
     * safe against concurrent map mutations. Kept in sync by the
     * producer task that polls every {@code trade.metrics.flush-ms} ms.
     */
    public long count(String strategy, String eventType, StrategyMetrics.Outcome outcome) {
        String key = strategy + "|" + eventType + "|" + outcome.name();
        AtomicLong c = counters.get(key);
        return c == null ? 0L : c.get();
    }
}
