package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class BollingerSqueeze {

    private final int period;
    private final double stdDevMultiplier;

    public BollingerSqueeze(int period, double stdDevMultiplier) {
        this.period = period;
        this.stdDevMultiplier = stdDevMultiplier;
    }

    public BollingerSqueeze() {
        this(20, 2.0);
    }

    public record Point(double middle, double upper, double lower, boolean squeeze) {
    }

    public List<Point> calculate(List<Candle> candles) {
        List<Point> results = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            int start = Math.max(0, i - period + 1);
            List<Double> closes = new ArrayList<>();
            for (int j = start; j <= i; j++) {
                closes.add(candles.get(j).closePaisa() / 100.0);
            }
            double mean = closes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = closes.stream()
                    .mapToDouble(v -> {
                        double d = v - mean;
                        return d * d;
                    })
                    .average()
                    .orElse(0.0);
            double std = Math.sqrt(variance);
            double upper = mean + stdDevMultiplier * std;
            double lower = mean - stdDevMultiplier * std;
            boolean squeeze = std < (mean * 0.01);
            results.add(new Point(mean, upper, lower, squeeze));
        }
        return results;
    }
}
