package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.RSI;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;

public final class RSIProvider implements IndicatorProvider {
    private final RSI rsi = new RSI(14);
    @Override public String name() { return "rsi"; }
    @Override public String displayName() { return "Relative Strength Index (14)"; }
    @Override public int minPeriod() { return 15; }
    @Override public List<Double> calculate(List<Candle> candles) { return rsi.calculate(candles); }
}
