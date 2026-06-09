package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.OBV;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;

public final class OBVProvider implements IndicatorProvider {
    private final OBV obv = new OBV();
    @Override public String name() { return "obv"; }
    @Override public String displayName() { return "On-Balance Volume"; }
    @Override public int minPeriod() { return 2; }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        List<Long> rawValues = obv.calculate(candles);
        return rawValues.stream().map(Long::doubleValue).toList();
    }
}
