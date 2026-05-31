package com.tradej.core.routing;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.support.MdcHelper;

/**
 * Deterministic symbol-to-shard routing for partitioned pipelines.
 *
 * <p>Moved from {@code trade-hotpath} to {@code trade-core} to be accessible
 * to both {@code trade-hotpath} and {@code trade-disruptor} without creating
 * a circular dependency (fixes P-01).
 */
public final class SymbolShardRouter {

    /** Singleton instance for use as a routing function. */
    public static final SymbolShardRouter INSTANCE = new SymbolShardRouter();

    private SymbolShardRouter() {
    }

    /**
     * Returns the shard index for a given symbol string.
     * Falls back to shard 0 when shardCount {@code <= 1} or symbol is blank.
     */
    public static int shardFor(String symbol, int shardCount) {
        if (shardCount <= 1) {
            return 0;
        }
        if (symbol == null || symbol.isBlank()) {
            return 0;
        }
        return Math.floorMod(symbol.hashCode(), shardCount);
    }

    /**
     * Returns the shard index for a domain event, extracting the symbol
     * via {@link MdcHelper#symbolOf} for deterministic routing.
     * Events without a resolvable symbol go to shard 0.
     */
    public static int shardFor(DomainEvent event, int shardCount) {
        if (shardCount <= 1) {
            return 0;
        }
        return MdcHelper.symbolOf(event)
                .map(symbol -> shardFor(symbol, shardCount))
                .orElse(0);
    }
}
