package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.SwingHighLow;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.ArrayList;
import java.util.List;

public final class SwingHighLowProvider implements IndicatorProvider {

    private static final int LOOKBACK = 5;

    @Override public String name() { return "swing-high-low"; }
    @Override public String displayName() { return "Swing High / Low"; }
    @Override public int minPeriod() { return LOOKBACK * 2 + 1; }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        // TODO: map typed Marker result (variable-length) to per-candle Double series;
        //       current signature mismatch — SwingHighLow emits markers asynchronously.
        SwingHighLow shl = new SwingHighLow(LOOKBACK);
        List<SwingHighLow.Marker> markers = shl.calculate(candles);
        // 0.0 for non-marker candles, marker price for swing markers.
        List<Double> result = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            result.add(0.0);
        }
        for (SwingHighLow.Marker m : markers) {
            for (int i = 0; i < candles.size(); i++) {
                if (candles.get(i).startTimeMs() == m.timeMs()) {
                    result.set(i, m.price());
                }
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> calculateTyped(List<Candle> candles, Class<T> recordType) {
        if (recordType != SwingHighLow.Marker.class) {
            throw new IllegalArgumentException(
                    "SwingHighLowProvider only produces SwingHighLow.Marker, requested " + recordType);
        }
        SwingHighLow shl = new SwingHighLow(LOOKBACK);
        return (List<T>) shl.calculate(candles);
    }
}
