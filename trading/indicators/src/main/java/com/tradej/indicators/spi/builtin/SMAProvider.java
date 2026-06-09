package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.SMA;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;

public final class SMAProvider implements IndicatorProvider {
    private final SMA sma = new SMA(20);
    @Override public String name() { return "sma"; }
    @Override public String displayName() { return "Simple Moving Average (20)"; }
    @Override public int minPeriod() { return 20; }
    @Override public List<Double> calculate(List<Candle> candles) { return sma.calculate(candles); }
}
