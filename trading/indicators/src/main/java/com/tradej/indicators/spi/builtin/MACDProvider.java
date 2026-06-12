package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.MACD;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;
import java.util.stream.Collectors;

public final class MACDProvider implements IndicatorProvider {

    private static final int FAST_PERIOD = 12;
    private static final int SLOW_PERIOD = 26;
    private static final int SIGNAL_PERIOD = 9;

    @Override public String name() { return "macd"; }
    @Override public String displayName() { return "Moving Average Convergence Divergence"; }
    @Override public int minPeriod() { return SLOW_PERIOD + SIGNAL_PERIOD; }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        // TODO: map typed Point result to Double; current signature mismatch
        MACD macd = new MACD(FAST_PERIOD, SLOW_PERIOD, SIGNAL_PERIOD);
        List<MACD.Point> typed = macd.calculate(candles);
        return typed.stream().map(p -> p.macd()).collect(Collectors.toList());
    }
}
