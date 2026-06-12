package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.SuperTrend;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;
import java.util.stream.Collectors;

public final class SuperTrendProvider implements IndicatorProvider {

    private static final int PERIOD = 10;
    private static final double MULTIPLIER = 3.0;

    @Override public String name() { return "supertrend"; }
    @Override public String displayName() { return "SuperTrend"; }
    @Override public int minPeriod() { return PERIOD + 1; }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        // TODO: map typed Point result to Double; current signature mismatch
        SuperTrend st = new SuperTrend(PERIOD, MULTIPLIER);
        List<SuperTrend.Point> typed = st.calculate(candles);
        return typed.stream().map(p -> p.value()).collect(Collectors.toList());
    }
}
