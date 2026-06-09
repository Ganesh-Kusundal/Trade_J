package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.EMA;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;

public final class EMAProvider implements IndicatorProvider {
    private final EMA ema = new EMA(20);
    @Override public String name() { return "ema"; }
    @Override public String displayName() { return "Exponential Moving Average (20)"; }
    @Override public int minPeriod() { return 20; }
    @Override public List<Double> calculate(List<Candle> candles) { return ema.calculate(candles); }
}
