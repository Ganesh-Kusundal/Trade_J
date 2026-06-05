package com.tradej.core.domain.port;

import java.util.Collections;
import java.util.Map;

/**
 * Provides the expected net positions for all traded symbols from the
 * strategy engine or position tracker.
 *
 * <p>The returned map is keyed by venue-qualified symbol
 * (e.g. {@code "NSE_EQ::SBIN"}) or plain symbol, mapping to the
 * expected net quantity. This is consumed by the reconciliation
 * pipeline to detect mismatches against live broker positions.
 */
@FunctionalInterface
public interface NetPositionProvider {

    record Position(String symbol, long quantity, long averagePricePaisa) {
        public long notionalValuePaisa(long ltpPaisa) {
            return Math.abs(quantity) * ltpPaisa;
        }

        public long unrealizedPnlPaisa(long ltpPaisa) {
            if (quantity == 0 || averagePricePaisa <= 0) return 0;
            return (ltpPaisa - averagePricePaisa) * quantity;
        }
    }

    /**
     * Returns the current expected net positions.
     *
     * @return unmodifiable map of symbol → expected Position
     */
    Map<String, Position> getPositions();

    /**
     * Returns the current net quantities (signed).
     *
     * @return map of symbol → quantity
     */
    default Map<String, Long> getNetPositions() {
        java.util.Map<String, Long> quantities = new java.util.HashMap<>();
        getPositions().forEach((s, p) -> quantities.put(s, p.quantity()));
        return quantities;
    }

    /**
     * Returns the position for a specific symbol.
     */
    default Position getPosition(String symbol) {
        return getPositions().getOrDefault(symbol, new Position(symbol, 0, 0));
    }

    /**
     * Returns the net quantity for a specific symbol.
     */
    default long getNetPosition(String symbol) {
        return getPosition(symbol).quantity();
    }

    /**
     * Returns a {@link NetPositionProvider} that always returns an empty map.
     */
    static NetPositionProvider empty() {
        return java.util.Collections::emptyMap;
    }
}
