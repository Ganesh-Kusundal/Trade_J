package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class RSI {

    private final int period;

    public RSI(int period) {
        this.period = period;
    }

    public List<Double> calculate(List<Candle> candles) {
        List<Double> result = new ArrayList<>();
        if (candles.size() < period + 1) {
            for (int i = 0; i < candles.size(); i++) {
                result.add(Double.NaN);
            }
            return result;
        }

        double avgGain = 0;
        double avgLoss = 0;

        for (int i = 0; i < candles.size(); i++) {
            if (i == 0) {
                result.add(Double.NaN);
                continue;
            }
            double change = (candles.get(i).closePaisa() - candles.get(i - 1).closePaisa()) / 100.0;
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);

            if (i <= period) {
                avgGain += gain;
                avgLoss += loss;
                if (i == period) {
                    avgGain /= period;
                    avgLoss /= period;
                    double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
                    result.add(100 - 100 / (1 + rs));
                } else {
                    result.add(Double.NaN);
                }
            } else {
                avgGain = (avgGain * (period - 1) + gain) / period;
                avgLoss = (avgLoss * (period - 1) + loss) / period;
                double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
                result.add(100 - 100 / (1 + rs));
            }
        }
        return result;
    }
}
