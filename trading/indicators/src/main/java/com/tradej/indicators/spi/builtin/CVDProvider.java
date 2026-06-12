package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.CVD;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;
import java.util.stream.Collectors;

public final class CVDProvider implements IndicatorProvider {

    @Override public String name() { return "cvd"; }
    @Override public String displayName() { return "Cumulative Volume Delta"; }
    @Override public int minPeriod() { return 1; }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        // TODO: map typed Point result to Double; current signature mismatch
        CVD cvd = new CVD();
        List<CVD.Point> typed = cvd.calculate(candles);
        return typed.stream().map(p -> p.cvd()).collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> calculateTyped(List<Candle> candles, Class<T> recordType) {
        if (recordType != CVD.Point.class) {
            throw new IllegalArgumentException(
                    "CVDProvider only produces CVD.Point, requested " + recordType);
        }
        return (List<T>) new CVD().calculate(candles);
    }
}
