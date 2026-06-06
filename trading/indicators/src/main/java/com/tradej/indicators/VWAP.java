package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class VWAP {

    public List<Double> calculate(List<Candle> candles) {
        List<Double> result = new ArrayList<>();
        double cumulativePv = 0;
        long cumulativeVolume = 0;

        for (Candle candle : candles) {
            double typicalPrice = (candle.highPaisa() + candle.lowPaisa() + candle.closePaisa()) / 300.0;
            cumulativePv += typicalPrice * candle.volume();
            cumulativeVolume += candle.volume();
            result.add(cumulativeVolume == 0 ? 0 : cumulativePv / cumulativeVolume);
        }
        return result;
    }
}
