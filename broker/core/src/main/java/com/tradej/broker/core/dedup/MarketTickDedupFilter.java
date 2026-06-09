package com.tradej.broker.core.dedup;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence-based market tick deduplication filter.
 *
 * <p>Prevents duplicate ticks from broker retransmission from propagating to the event bus.
 * Keyed by {@code symbol:segment}, tracks the last seen sequence ID per instrument.
 * Drops ticks with sequence IDs less than or equal to the last seen value.
 *
 * <p>Thread-safe. Suitable for use in high-throughput WebSocket callbacks.
 */
public final class MarketTickDedupFilter {

    private final ConcurrentHashMap<String, Long> lastSequenceBySymbol = new ConcurrentHashMap<>();
    private final int maxSize;

    public MarketTickDedupFilter() {
        this(10_000);
    }

    public MarketTickDedupFilter(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Returns {@code true} if this tick is a duplicate and should be dropped.
     *
     * @param symbol     instrument symbol
     * @param segment    exchange segment name
     * @param sequenceId broker-provided sequence ID (or timestamp-based fallback)
     * @return true if duplicate, false if unique
     */
    public boolean isDuplicate(String symbol, String segment, long sequenceId) {
        String key = symbol + ":" + segment;
        Long last = lastSequenceBySymbol.get(key);
        if (last != null && sequenceId <= last) {
            return true;
        }
        lastSequenceBySymbol.put(key, sequenceId);
        evictIfNeeded();
        return false;
    }

    /**
     * Reset all dedup state (e.g. on reconnect).
     */
    public void reset() {
        lastSequenceBySymbol.clear();
    }

    public int size() {
        return lastSequenceBySymbol.size();
    }

    private void evictIfNeeded() {
        if (lastSequenceBySymbol.size() > maxSize) {
            lastSequenceBySymbol.clear();
        }
    }
}
