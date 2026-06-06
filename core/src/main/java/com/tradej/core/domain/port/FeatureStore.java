package com.tradej.core.domain.port;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.model.FeatureVector;

import java.util.Optional;

/**
 * Port interface for the time-series feature store.
 *
 * <p>Ingests {@link DomainEvent}s (ticks, candles) and exposes computed
 * feature vectors for ML inference and strategy consumption.
 */
public interface FeatureStore {

    /**
     * Ingest a domain event into the feature store.
     * Supported event types: {@link com.tradej.core.domain.event.MarketTickEvent},
     * {@link com.tradej.core.domain.event.CandleClosed},
     * {@link com.tradej.core.domain.event.CandleDeveloping}.
     */
    void feed(DomainEvent event);

    /**
     * Compute and return the latest feature vector for the given symbol
     * and interval, based on the most recent {@code candleCount} candles.
     *
     * @param symbol      trading symbol (e.g. "SBIN")
     * @param interval    candle interval (e.g. "5m", "1d")
     * @param lookback    number of historical candles to include in computations
     * @return feature vector, or empty if insufficient data
     */
    Optional<FeatureVector> getFeatures(String symbol, String interval, int lookback);
}
