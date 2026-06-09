package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.VWAP;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;

public final class VWAPProvider implements IndicatorProvider {
    private final VWAP vwap = new VWAP();
    @Override public String name() { return "vwap"; }
    @Override public String displayName() { return "Volume Weighted Average Price"; }
    @Override public int minPeriod() { return 1; }
    @Override public List<Double> calculate(List<Candle> candles) { return vwap.calculate(candles); }
}
