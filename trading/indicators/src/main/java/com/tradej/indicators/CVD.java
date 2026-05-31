package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class CVD {

    public record Point(double cvd, double volumeDelta) {
    }

    public List<Point> calculate(List<Candle> candles) {
        List<Point> results = new ArrayList<>();
        double cumulative = 0.0;
        for (Candle candle : candles) {
            double volumeDelta = candle.closePaisa() >= candle.openPaisa()
                    ? candle.volume()
                    : -candle.volume();
            cumulative += volumeDelta;
            results.add(new Point(cumulative, volumeDelta));
        }
        return results;
    }
}
