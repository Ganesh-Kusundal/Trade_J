package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class SuperTrend {

    private final int period;
    private final double multiplier;

    public SuperTrend() {
        this(10, 3.0);
    }

    public SuperTrend(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
    }

    public record Point(double value, boolean bullish) {
    }

    public List<Point> calculate(List<Candle> candles) {
        List<Point> result = new ArrayList<>();
        List<Double> atrValues = new ATR(period).calculate(candles);

        double upperBand = Double.NaN;
        double lowerBand = Double.NaN;
        double superTrend = Double.NaN;
        boolean bullish = true;

        for (int i = 0; i < candles.size(); i++) {
            if (Double.isNaN(atrValues.get(i))) {
                result.add(new Point(Double.NaN, true));
                continue;
            }

            double hl2 = (candles.get(i).highPaisa() + candles.get(i).lowPaisa()) / 200.0;
            double atr = atrValues.get(i);
            double newUpper = hl2 + multiplier * atr;
            double newLower = hl2 - multiplier * atr;

            if (!Double.isNaN(upperBand) && candles.get(i - 1).closePaisa() / 100.0 > upperBand) {
                newUpper = Math.min(newUpper, upperBand);
            }
            if (!Double.isNaN(lowerBand) && candles.get(i - 1).closePaisa() / 100.0 < lowerBand) {
                newLower = Math.max(newLower, lowerBand);
            }

            upperBand = newUpper;
            lowerBand = newLower;

            double close = candles.get(i).closePaisa() / 100.0;
            if (Double.isNaN(superTrend)) {
                superTrend = lowerBand;
                bullish = true;
            } else if (superTrend == upperBand) {
                if (close > upperBand) {
                    superTrend = lowerBand;
                    bullish = true;
                }
            } else {
                if (close < lowerBand) {
                    superTrend = upperBand;
                    bullish = false;
                }
            }

            result.add(new Point(superTrend, bullish));
        }
        return result;
    }
}
