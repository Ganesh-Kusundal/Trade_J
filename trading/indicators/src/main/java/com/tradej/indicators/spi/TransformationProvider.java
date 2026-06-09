package com.tradej.indicators.spi;

import com.tradej.core.domain.model.Candle;

import java.util.List;
import java.util.Map;

/**
 * Service Provider Interface for pluggable candle transformations.
 *
 * <p>Transformations convert standard OHLCV candles into alternative
 * representations (Renko, Heikin Ashi, Range Bars, etc.).
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}.
 * To register a new transformation:
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Create {@code META-INF/services/com.tradej.indicators.spi.TransformationProvider}</li>
 * </ol>
 */
public interface TransformationProvider {

    /**
     * Unique name for this transformation (e.g. "renko", "heikin-ashi", "range-bars").
     */
    String name();

    /**
     * Human-readable display name.
     */
    default String displayName() {
        return name();
    }

    /**
     * Transform a list of standard candles into the alternative representation.
     *
     * @param input  ordered list of standard OHLCV candles (oldest first)
     * @param params transformation-specific parameters (e.g. brick size for Renko)
     * @return transformed candle list (may differ in size from input)
     */
    List<Candle> transform(List<Candle> input, Map<String, Object> params);

    /**
     * Default parameters for this transformation.
     */
    default Map<String, Object> defaultParams() {
        return Map.of();
    }

    /**
     * Minimum number of input candles required.
     */
    default int minInputSize() {
        return 2;
    }

    default String version() {
        return "1.0.0";
    }
}
