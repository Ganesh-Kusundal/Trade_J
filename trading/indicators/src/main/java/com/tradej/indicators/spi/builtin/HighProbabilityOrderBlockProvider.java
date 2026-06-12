package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.HighProbabilityOrderBlock;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.ArrayList;
import java.util.List;

public final class HighProbabilityOrderBlockProvider implements IndicatorProvider {

    @Override public String name() { return "order-block"; }
    @Override public String displayName() { return "High-Probability Order Block"; }
    @Override public int minPeriod() { return 3; }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        // TODO: map typed Zone result (variable-length) to per-candle Double series;
        //       current signature mismatch — OrderBlock emits zones asynchronously.
        HighProbabilityOrderBlock ob = new HighProbabilityOrderBlock();
        List<HighProbabilityOrderBlock.Zone> zones = ob.calculate(candles);
        // Mark candle indices that fall inside a detected zone with the zone's top price,
        // and 0.0 elsewhere. Keeps the series aligned with the input candle list.
        List<Double> result = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            result.add(0.0);
        }
        for (HighProbabilityOrderBlock.Zone z : zones) {
            for (int i = 0; i < candles.size(); i++) {
                long t = candles.get(i).startTimeMs();
                if (t >= z.startTimeMs() && t <= z.endTimeMs()) {
                    result.set(i, z.top());
                }
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> calculateTyped(List<Candle> candles, Class<T> recordType) {
        if (recordType != HighProbabilityOrderBlock.Zone.class) {
            throw new IllegalArgumentException(
                    "HighProbabilityOrderBlockProvider only produces HighProbabilityOrderBlock.Zone, requested " + recordType);
        }
        return (List<T>) new HighProbabilityOrderBlock().calculate(candles);
    }
}
