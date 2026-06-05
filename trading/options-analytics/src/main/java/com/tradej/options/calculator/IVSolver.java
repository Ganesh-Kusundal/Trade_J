package com.tradej.options.calculator;

import com.tradej.core.domain.value.OptionType;

/**
 * Newton-Raphson implied volatility solver using Black-Scholes prices.
 */
public final class IVSolver {

    private static final int MAX_ITER = 50;
    private static final double TOLERANCE = 1e-6;

    private IVSolver() {
    }

    public static double solve(
            double marketPrice,
            double spot,
            double strike,
            double timeToExpiryYears,
            double riskFreeRate,
            OptionType optionType
    ) {
        if (marketPrice <= 0 || spot <= 0 || strike <= 0 || timeToExpiryYears <= 0) {
            return Double.NaN;
        }
        double vol = 0.25;
        for (int i = 0; i < MAX_ITER; i++) {
            double price = BlackScholesCalculator.price(spot, strike, timeToExpiryYears, vol, riskFreeRate, optionType);
            double diff = price - marketPrice;
            if (Math.abs(diff) < TOLERANCE) {
                return vol;
            }
            Double vegaBox = BlackScholesCalculator.compute(spot, strike, timeToExpiryYears, vol, riskFreeRate, optionType).vega();
            double vega = vegaBox == null ? 0.0 : vegaBox;
            if (Math.abs(vega) < 1e-12) {
                break;
            }
            vol -= diff / (vega * 100.0);
            if (vol <= 0.001) {
                vol = 0.001;
            }
            if (vol > 5.0) {
                vol = 5.0;
            }
        }
        return vol;
    }
}
