package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class ATR {

    private final int period;

    public ATR(int period) {
        this.period = period;
    }

    public List<Double> calculate(List<Candle> candles) {
        List<Double> result = new ArrayList<>();
        if (candles.isEmpty()) return result;

        double atr = Double.NaN;
        for (int i = 0; i < candles.size(); i++) {
            double high = candles.get(i).highPaisa() / 100.0;
            double low = candles.get(i).lowPaisa() / 100.0;
            double prevClose = i > 0 ? candles.get(i - 1).closePaisa() / 100.0 : high;
            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));

            if (i < period) {
                result.add(Double.NaN);
            } else if (i == period) {
                double sum = 0;
                for (int j = 1; j <= period; j++) {
                    double h = candles.get(j).highPaisa() / 100.0;
                    double l = candles.get(j).lowPaisa() / 100.0;
                    double pc = candles.get(j - 1).closePaisa() / 100.0;
                    sum += Math.max(h - l, Math.max(Math.abs(h - pc), Math.abs(l - pc)));
                }
                atr = sum / period;
                result.add(atr);
            } else {
                atr = (atr * (period - 1) + tr) / period;
                result.add(atr);
            }
        }
        return result;
    }
}
