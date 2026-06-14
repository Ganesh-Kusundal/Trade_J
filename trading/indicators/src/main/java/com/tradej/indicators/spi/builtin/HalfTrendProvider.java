package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.HalfTrend;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class HalfTrendProvider implements IndicatorProvider {

    private static final int AMPLITUDE = 2;
    private static final int CHANNEL_DEVIATION = 2;
    private static final int ATR_PERIOD = 100;

    @Override public String name() { return "halftrend"; }
    @Override public String displayName() { return "HalfTrend"; }
    @Override public int minPeriod() { return 1; }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        // TODO: map typed Point result to Double; current signature mismatch
        HalfTrend ht = new HalfTrend(AMPLITUDE, CHANNEL_DEVIATION, ATR_PERIOD);
        List<HalfTrend.Point> typed = ht.calculate(candles);
        return typed.stream().map(p -> p.value()).collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> calculateTyped(List<Candle> candles, Class<T> recordType) {
        if (recordType != HalfTrend.Point.class) {
            throw new IllegalArgumentException(
                    "HalfTrendProvider only produces HalfTrend.Point, requested " + recordType);
        }
        HalfTrend ht = new HalfTrend(AMPLITUDE, CHANNEL_DEVIATION, ATR_PERIOD);
        return (List<T>) ht.calculate(candles);
    }
}
