package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class SwingHighLow {

    private final int lookback;

    public SwingHighLow(int lookback) {
        this.lookback = lookback;
    }

    public SwingHighLow() {
        this(5);
    }

    public record Marker(String type, long timeMs, double price) {
    }

    public List<Marker> calculate(List<Candle> candles) {
        List<Marker> markers = new ArrayList<>();
        for (int i = lookback; i < candles.size() - lookback; i++) {
            Candle current = candles.get(i);
            boolean swingHigh = true;
            boolean swingLow = true;
            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) {
                    continue;
                }
                if (candles.get(j).highPaisa() >= current.highPaisa()) {
                    swingHigh = false;
                }
                if (candles.get(j).lowPaisa() <= current.lowPaisa()) {
                    swingLow = false;
                }
            }
            if (swingHigh) {
                markers.add(new Marker("swing_high", current.startTimeMs(), current.highPaisa() / 100.0));
            }
            if (swingLow) {
                markers.add(new Marker("swing_low", current.startTimeMs(), current.lowPaisa() / 100.0));
            }
        }
        return markers;
    }
}
