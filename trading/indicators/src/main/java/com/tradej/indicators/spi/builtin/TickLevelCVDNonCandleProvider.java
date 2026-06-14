package com.tradej.indicators.spi.builtin;

import com.tradej.indicators.TickLevelCVD;
import com.tradej.indicators.spi.NonCandleIndicatorProvider;

import java.util.List;

/**
 * Non-candle {@link TickLevelCVD} provider. Consumes a stream of ticks
 * ({@code [pricePaisa, volume]} pairs) and produces a CVD snapshot.
 *
 * <p>This replaces the prior {@code IndicatorProvider} workaround that
 * mis-registered {@link TickLevelCVD} as a per-candle series with a
 * constant-per-call return value.
 */
public final class TickLevelCVDNonCandleProvider implements NonCandleIndicatorProvider {

    @Override public String name() { return "tick-level-cvd"; }
    @Override public String displayName() { return "Tick-Level Cumulative Volume Delta"; }
    @Override public String version() { return "1.0.0"; }

    @Override
    public TickLevelCVD.CvdSnapshot computeFromTicks(List<long[]> ticks) {
        TickLevelCVD cvd = new TickLevelCVD();
        for (long[] tick : ticks) {
            cvd.onTick(tick[0], tick[1]);
        }
        return cvd.snapshot();
    }
}
