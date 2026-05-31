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

    /**
     * Returns the current expected net positions.
     *
     * <p>An empty map signals "no expectations" — reconciliation
     * will not flag broker-held positions as mismatches since there
     * is no expected quantity to compare against.
     *
     * @return unmodifiable map of symbol → expected net quantity
     */
    Map<String, Long> getNetPositions();

    /**
     * Returns a {@link NetPositionProvider} that always returns an empty map.
     * Suitable as a default when no strategy engine is wired.
     */
    static NetPositionProvider empty() {
        return () -> Collections.emptyMap();
    }
}
