package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class HalfTrend {

    private final int amplitude;
    private final int channelDeviation;
    private final int atrPeriod;

    public HalfTrend(int amplitude, int channelDeviation, int atrPeriod) {
        this.amplitude = amplitude;
        this.channelDeviation = channelDeviation;
        this.atrPeriod = atrPeriod;
    }

    public HalfTrend() {
        this(2, 2, 100);
    }

    public record Point(double value, String direction, double high, double low) {
    }

    public List<Point> calculate(List<Candle> candles) {
        List<Point> results = new ArrayList<>();
        if (candles.isEmpty()) {
            return results;
        }

        List<Double> atr = computeAtr(candles);
        int trend = 0;
        double maxLow = 0.0;
        double minHigh = 0.0;
        double lastHalfTrend = 0.0;

        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            double dev = channelDeviation * atr.get(i);
            int startIdx = Math.max(0, i - amplitude + 1);
            double highPrice = Double.NEGATIVE_INFINITY;
            double lowPrice = Double.POSITIVE_INFINITY;
            double sumHigh = 0.0;
            double sumLow = 0.0;
            int count = 0;
            for (int k = startIdx; k <= i; k++) {
                Candle c = candles.get(k);
                highPrice = Math.max(highPrice, c.highPaisa() / 100.0);
                lowPrice = Math.min(lowPrice, c.lowPaisa() / 100.0);
                sumHigh += c.highPaisa() / 100.0;
                sumLow += c.lowPaisa() / 100.0;
                count++;
            }
            double highma = count > 0 ? sumHigh / count : 0.0;
            double lowma = count > 0 ? sumLow / count : 0.0;
            double close = candle.closePaisa() / 100.0;
            double open = candle.openPaisa() / 100.0;

            if (trend == 0) {
                trend = close >= open ? 1 : -1;
                maxLow = lowPrice - dev;
                minHigh = highPrice + dev;
                lastHalfTrend = close;
            }

            double currentHalfTrend = lastHalfTrend;
            if (trend == 1) {
                double prospectiveMaxLow = lowPrice - dev;
                if (prospectiveMaxLow > maxLow) {
                    maxLow = prospectiveMaxLow;
                }
                if (highma < maxLow && close < maxLow) {
                    trend = -1;
                    minHigh = highPrice + dev;
                    maxLow = prospectiveMaxLow;
                }
            } else {
                double prospectiveMinHigh = highPrice + dev;
                if (prospectiveMinHigh < minHigh) {
                    minHigh = prospectiveMinHigh;
                }
                if (lowma > minHigh && close > minHigh) {
                    trend = 1;
                    maxLow = lowPrice - dev;
                    minHigh = prospectiveMinHigh;
                }
            }

            currentHalfTrend = trend == 1 ? maxLow : minHigh;
            lastHalfTrend = currentHalfTrend;
            results.add(new Point(
                    currentHalfTrend,
                    trend == 1 ? "up" : "down",
                    maxLow,
                    minHigh
            ));
        }
        return results;
    }

    private static List<Double> computeAtr(List<Candle> candles) {
        List<Double> tr = new ArrayList<>();
        List<Double> atr = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            double trVal;
            if (i == 0) {
                trVal = (candle.highPaisa() - candle.lowPaisa()) / 100.0;
            } else {
                double prevClose = candles.get(i - 1).closePaisa() / 100.0;
                trVal = Math.max(
                        (candle.highPaisa() - candle.lowPaisa()) / 100.0,
                        Math.max(
                                Math.abs(candle.highPaisa() / 100.0 - prevClose),
                                Math.abs(candle.lowPaisa() / 100.0 - prevClose)
                        )
                );
            }
            tr.add(trVal);
        }
        double runningSum = 0.0;
        double currentAtr = 0.0;
        int atrPeriod = 100;
        for (int i = 0; i < tr.size(); i++) {
            runningSum += tr.get(i);
            if (i < atrPeriod) {
                currentAtr = runningSum / (i + 1);
            } else {
                currentAtr = (currentAtr * (atrPeriod - 1) + tr.get(i)) / atrPeriod;
            }
            atr.add(currentAtr);
        }
        return atr;
    }
}
