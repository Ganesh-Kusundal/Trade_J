package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class EMA {

    private final int period;

    public EMA(int period) {
        this.period = period;
    }

    public List<Double> calculate(List<Candle> candles) {
        List<Double> result = new ArrayList<>();
        double multiplier = 2.0 / (period + 1);
        double ema = Double.NaN;

        for (int i = 0; i < candles.size(); i++) {
            double close = candles.get(i).closePaisa() / 100.0;
            if (i < period - 1) {
                result.add(Double.NaN);
            } else if (i == period - 1) {
                double sum = 0;
                for (int j = 0; j < period; j++) {
                    sum += candles.get(j).closePaisa() / 100.0;
                }
                ema = sum / period;
                result.add(ema);
            } else {
                ema = (close - ema) * multiplier + ema;
                result.add(ema);
            }
        }
        return result;
    }
}
