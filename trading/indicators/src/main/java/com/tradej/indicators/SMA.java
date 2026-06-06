package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class SMA {

    private final int period;

    public SMA(int period) {
        this.period = period;
    }

    public List<Double> calculate(List<Candle> candles) {
        List<Double> result = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < candles.size(); i++) {
            double close = candles.get(i).closePaisa() / 100.0;
            sum += close;
            if (i >= period) {
                sum -= candles.get(i - period).closePaisa() / 100.0;
            }
            if (i >= period - 1) {
                result.add(sum / period);
            } else {
                result.add(Double.NaN);
            }
        }
        return result;
    }
}
