package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

public final class OBV {

    public List<Long> calculate(List<Candle> candles) {
        List<Long> result = new ArrayList<>();
        long obv = 0;
        for (int i = 0; i < candles.size(); i++) {
            if (i == 0) {
                obv = candles.get(i).volume();
            } else {
                long prevClose = candles.get(i - 1).closePaisa();
                long currClose = candles.get(i).closePaisa();
                if (currClose > prevClose) {
                    obv += candles.get(i).volume();
                } else if (currClose < prevClose) {
                    obv -= candles.get(i).volume();
                }
            }
            result.add(obv);
        }
        return result;
    }
}
