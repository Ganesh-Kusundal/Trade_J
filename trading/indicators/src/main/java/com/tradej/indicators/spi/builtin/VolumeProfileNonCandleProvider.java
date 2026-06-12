package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Trade;
import com.tradej.indicators.VolumeProfile;
import com.tradej.indicators.spi.NonCandleIndicatorProvider;

import java.util.List;

/**
 * Non-candle {@link VolumeProfile} provider. Consumes a stream of trade
 * events (price, volume) and produces a populated {@link VolumeProfile}.
 *
 * <p>This replaces the prior {@code IndicatorProvider} workaround that
 * mis-registered {@link VolumeProfile} as a per-candle series with a
 * constant-per-call return value.
 */
public final class VolumeProfileNonCandleProvider implements NonCandleIndicatorProvider {

    private static final long DEFAULT_TICK_SIZE_PAISA = 100L;

    @Override public String name() { return "volume-profile"; }
    @Override public String displayName() { return "Volume Profile"; }
    @Override public String version() { return "1.0.0"; }

    @Override
    public VolumeProfile computeFromTrades(List<Trade> trades) {
        VolumeProfile vp = new VolumeProfile(DEFAULT_TICK_SIZE_PAISA);
        for (Trade t : trades) {
            vp.addTrade(t.pricePaisa(), t.quantity());
        }
        return vp;
    }
}
