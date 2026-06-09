package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.ATR;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;

public final class ATRProvider implements IndicatorProvider {
    private final ATR atr = new ATR(14);
    @Override public String name() { return "atr"; }
    @Override public String displayName() { return "Average True Range (14)"; }
    @Override public int minPeriod() { return 15; }
    @Override public List<Double> calculate(List<Candle> candles) { return atr.calculate(candles); }
}
