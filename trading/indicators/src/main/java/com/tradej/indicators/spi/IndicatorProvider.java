package com.tradej.indicators.spi;

import com.tradej.core.domain.model.Candle;

import java.util.List;

/**
 * Service Provider Interface for pluggable technical indicators.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}.
 * To register a new indicator:
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Create {@code META-INF/services/com.tradej.indicators.spi.IndicatorProvider}
 *       containing the fully qualified class name</li>
 * </ol>
 *
 * <p>Example:
 * <pre>
 * public class KeltnerChannelProvider implements IndicatorProvider {
 *     public String name() { return "keltner"; }
 *     public int minPeriod() { return 20; }
 *     public List&lt;Double&gt; calculate(List&lt;Candle&gt; candles) { ... }
 * }
 * </pre>
 */
public interface IndicatorProvider {

    /**
     * Unique name for this indicator (e.g. "rsi", "ema", "macd").
     * Used as the lookup key in {@link IndicatorRegistry}.
     */
    String name();

    /**
     * Human-readable display name (e.g. "Relative Strength Index").
     */
    default String displayName() {
        return name();
    }

    /**
     * Minimum number of candles required before this indicator produces valid output.
     * The registry will return NaN values for indices before this period.
     */
    int minPeriod();

    /**
     * Calculate the indicator values for the given candle series.
     *
     * @param candles ordered list of candles (oldest first)
     * @return list of indicator values, same size as input; NaN for insufficient data
     */
    List<Double> calculate(List<Candle> candles);

    /**
     * Semantic version of this indicator provider.
     */
    default String version() {
        return "1.0.0";
    }
}
