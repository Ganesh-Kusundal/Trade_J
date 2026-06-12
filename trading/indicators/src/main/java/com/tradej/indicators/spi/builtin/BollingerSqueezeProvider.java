package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.BollingerSqueeze;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;
import java.util.stream.Collectors;

public final class BollingerSqueezeProvider implements IndicatorProvider {

    private static final int PERIOD = 20;
    private static final double STD_DEV_MULTIPLIER = 2.0;

    @Override public String name() { return "bollinger-squeeze"; }
    @Override public String displayName() { return "Bollinger Bands Squeeze"; }
    @Override public int minPeriod() { return PERIOD; }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        // TODO: map typed Point result to Double; current signature mismatch
        BollingerSqueeze bs = new BollingerSqueeze(PERIOD, STD_DEV_MULTIPLIER);
        List<BollingerSqueeze.Point> typed = bs.calculate(candles);
        return typed.stream().map(p -> p.middle()).collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> calculateTyped(List<Candle> candles, Class<T> recordType) {
        if (recordType != BollingerSqueeze.Point.class) {
            throw new IllegalArgumentException(
                    "BollingerSqueezeProvider only produces BollingerSqueeze.Point, requested " + recordType);
        }
        BollingerSqueeze bs = new BollingerSqueeze(PERIOD, STD_DEV_MULTIPLIER);
        return (List<T>) bs.calculate(candles);
    }
}
