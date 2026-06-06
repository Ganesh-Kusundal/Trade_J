package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class MACD {

    private final int fastPeriod;
    private final int slowPeriod;
    private final int signalPeriod;

    public MACD() {
        this(12, 26, 9);
    }

    public MACD(int fastPeriod, int slowPeriod, int signalPeriod) {
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
    }

    public record Point(double macd, double signal, double histogram) {
    }

    public List<Point> calculate(List<Candle> candles) {
        List<Double> fastEma = new EMA(fastPeriod).calculate(candles);
        List<Double> slowEma = new EMA(slowPeriod).calculate(candles);

        List<Double> macdLine = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            if (Double.isNaN(fastEma.get(i)) || Double.isNaN(slowEma.get(i))) {
                macdLine.add(Double.NaN);
            } else {
                macdLine.add(fastEma.get(i) - slowEma.get(i));
            }
        }

        double multiplier = 2.0 / (signalPeriod + 1);
        double signalEma = Double.NaN;
        List<Point> result = new ArrayList<>();

        int validCount = 0;
        for (int i = 0; i < macdLine.size(); i++) {
            double macd = macdLine.get(i);
            if (Double.isNaN(macd)) {
                result.add(new Point(Double.NaN, Double.NaN, Double.NaN));
                continue;
            }
            validCount++;
            if (validCount < signalPeriod) {
                result.add(new Point(macd, Double.NaN, Double.NaN));
            } else if (validCount == signalPeriod) {
                double sum = 0;
                int count = 0;
                for (int j = i; j >= 0 && count < signalPeriod; j--) {
                    if (!Double.isNaN(macdLine.get(j))) {
                        sum += macdLine.get(j);
                        count++;
                    }
                }
                signalEma = sum / signalPeriod;
                result.add(new Point(macd, signalEma, macd - signalEma));
            } else {
                signalEma = (macd - signalEma) * multiplier + signalEma;
                result.add(new Point(macd, signalEma, macd - signalEma));
            }
        }
        return result;
    }
}
