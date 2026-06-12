package com.tradej.indicators.spi;

import com.tradej.core.domain.model.Trade;
import com.tradej.indicators.TickLevelCVD;
import com.tradej.indicators.VolumeProfile;

import java.util.List;

/**
 * SPI for indicators that are NOT computed from a per-candle series.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@link VolumeProfile} — built from a stream of trade events
 *       (price, volume) and exposes a price-at-volume distribution
 *       (POC, Value Area, HVN/LVN).</li>
 *   <li>{@link TickLevelCVD} — built from a stream of ticks
 *       (price, volume) and exposes a cumulative volume delta.</li>
 * </ul>
 *
 * <p>These indicators are intentionally separate from {@link IndicatorProvider},
 * which assumes a candle-aligned series. The two SPIs are discovered
 * independently and must not be conflated: a per-candle series is the wrong
 * shape for trade-book or tick-stream indicators.
 *
 * <p>Each implementation overrides only the computation method that matches
 * its real input shape. The other method throws
 * {@link UnsupportedOperationException} by default, which is a deliberate
 * signal that the provider does not support that input type.
 */
public interface NonCandleIndicatorProvider {

    /**
     * Stable identifier for this provider (e.g. "volume-profile", "tick-level-cvd").
     * Used as the lookup key in {@link NonCandleIndicatorRegistry}.
     */
    String name();

    /**
     * Human-readable display name (e.g. "Volume Profile").
     */
    default String displayName() {
        return name();
    }

    /**
     * Semantic version of this provider.
     */
    default String version() {
        return "1.0.0";
    }

    /**
     * Compute the indicator from a stream of trade events.
     *
     * <p>Default implementation throws {@link UnsupportedOperationException}.
     * Providers that consume trade-book data (e.g. {@link VolumeProfile})
     * must override this method.
     *
     * @param trades ordered list of trade events (oldest first)
     * @return the populated {@link VolumeProfile} result
     * @throws UnsupportedOperationException if this provider does not
     *         consume trade events
     */
    default VolumeProfile computeFromTrades(List<Trade> trades) {
        throw new UnsupportedOperationException(
                name() + " does not consume trade events; "
                        + "use computeFromTicks(...) instead.");
    }

    /**
     * Compute the indicator from a stream of ticks.
     *
     * <p>Each tick is a {@code long[2]} of {@code [pricePaisa, volume]}.
     * Tick is intentionally a primitive pair rather than a domain record
     * because the underlying {@link TickLevelCVD#onTick(long, long)} API
     * only consumes a price/volume pair, and introducing a new domain
     * {@code Tick} type would expand the scope of this SPI.
     *
     * <p>Default implementation throws {@link UnsupportedOperationException}.
     * Providers that consume tick data (e.g. {@link TickLevelCVD})
     * must override this method.
     *
     * @param ticks ordered list of {@code [pricePaisa, volume]} pairs (oldest first)
     * @return the populated {@link TickLevelCVD.CvdSnapshot} result
     * @throws UnsupportedOperationException if this provider does not
     *         consume tick data
     */
    default TickLevelCVD.CvdSnapshot computeFromTicks(List<long[]> ticks) {
        throw new UnsupportedOperationException(
                name() + " does not consume tick data; "
                        + "use computeFromTrades(...) instead.");
    }
}
