package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class HighProbabilityOrderBlock {

    public record Zone(long startTimeMs, long endTimeMs, double top, double bottom, String bias) {
    }

    public List<Zone> calculate(List<Candle> candles) {
        List<Zone> zones = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            Candle prev = candles.get(i - 1);
            Candle current = candles.get(i);
            if (prev.closePaisa() < prev.openPaisa() && current.closePaisa() > current.openPaisa()
                    && current.closePaisa() > prev.openPaisa()) {
                zones.add(new Zone(
                        prev.startTimeMs(),
                        current.endTimeMs(),
                        prev.openPaisa() / 100.0,
                        prev.lowPaisa() / 100.0,
                        "bullish"
                ));
            }
            if (prev.closePaisa() > prev.openPaisa() && current.closePaisa() < current.openPaisa()
                    && current.closePaisa() < prev.openPaisa()) {
                zones.add(new Zone(
                        prev.startTimeMs(),
                        current.endTimeMs(),
                        prev.highPaisa() / 100.0,
                        prev.openPaisa() / 100.0,
                        "bearish"
                ));
            }
        }
        return zones;
    }
}
