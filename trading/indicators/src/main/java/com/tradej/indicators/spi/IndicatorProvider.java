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
     * Typed result variant for providers whose underlying indicator emits
     * multi-field records (e.g. {@code HalfTrend.Point}, {@code CVD.Point},
     * {@code BollingerSqueeze.Point}, {@code SwingHighLow.Marker},
     * {@code HighProbabilityOrderBlock.Zone}) that the untyped
     * {@link #calculate(List)} contract cannot faithfully represent.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}; only providers that own a
     * multi-field typed result need to override it. The simple single-value
     * providers (RSI, EMA, SMA, ATR, VWAP, OBV) keep using
     * {@link #calculate(List)} unchanged.
     *
     * <p>{@code recordType} lets the caller declare the expected record
     * type at the call site so the provider can return a
     * {@code List<HalfTrend.Point>} (etc.) that the caller can cast without
     * a raw-type warning at the call boundary.
     *
     * @param candles    ordered list of candles (oldest first)
     * @param recordType the expected record class; providers should use this
     *                   to validate the request and reject mismatched types
     * @param <T>        the record type produced by this provider
     * @return typed per-candle records
     * @throws UnsupportedOperationException if this provider does not support
     *                                       typed results
     */
    default <T> List<T> calculateTyped(List<Candle> candles, Class<T> recordType) {
        throw new UnsupportedOperationException(
                name() + " does not support typed results; use calculate()");
    }

    /**
     * Semantic version of this indicator provider.
     */
    default String version() {
        return "1.0.0";
    }
}
