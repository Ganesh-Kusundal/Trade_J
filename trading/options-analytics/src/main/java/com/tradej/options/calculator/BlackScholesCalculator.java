package com.tradej.options.calculator;

import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.value.OptionType;

/**
 * Black-Scholes option pricing and Greeks for European options.
 */
public final class BlackScholesCalculator {

    private BlackScholesCalculator() {
    }

    public static OptionGreeks compute(
            double spot,
            double strike,
            double timeToExpiryYears,
            double volatility,
            double riskFreeRate,
            OptionType optionType
    ) {
        if (timeToExpiryYears <= 0 || volatility <= 0 || spot <= 0 || strike <= 0) {
            return OptionGreeks.UNKNOWN;
        }
        double sqrtT = Math.sqrt(timeToExpiryYears);
        double d1 = (Math.log(spot / strike) + (riskFreeRate + 0.5 * volatility * volatility) * timeToExpiryYears)
                / (volatility * sqrtT);
        double d2 = d1 - volatility * sqrtT;
        double nd1 = normCdf(d1);
        double nd2 = normCdf(d2);
        double npd1 = normPdf(d1);
        boolean call = optionType == OptionType.CALL;
        double delta = call ? nd1 : nd1 - 1.0;
        double gamma = npd1 / (spot * volatility * sqrtT);
        double vega = spot * npd1 * sqrtT / 100.0;
        double theta = call
                ? (-spot * npd1 * volatility / (2 * sqrtT) - riskFreeRate * strike * Math.exp(-riskFreeRate * timeToExpiryYears) * nd2) / 365.0
                : (-spot * npd1 * volatility / (2 * sqrtT) + riskFreeRate * strike * Math.exp(-riskFreeRate * timeToExpiryYears) * normCdf(-d2)) / 365.0;
        return new OptionGreeks(delta, theta, gamma, vega, volatility);
    }

    public static double price(
            double spot,
            double strike,
            double timeToExpiryYears,
            double volatility,
            double riskFreeRate,
            OptionType optionType
    ) {
        if (timeToExpiryYears <= 0 || volatility <= 0) {
            return Math.max(0, spot - strike);
        }
        double sqrtT = Math.sqrt(timeToExpiryYears);
        double d1 = (Math.log(spot / strike) + (riskFreeRate + 0.5 * volatility * volatility) * timeToExpiryYears)
                / (volatility * sqrtT);
        double d2 = d1 - volatility * sqrtT;
        if (optionType == OptionType.CALL) {
            return spot * normCdf(d1) - strike * Math.exp(-riskFreeRate * timeToExpiryYears) * normCdf(d2);
        }
        return strike * Math.exp(-riskFreeRate * timeToExpiryYears) * normCdf(-d2) - spot * normCdf(-d1);
    }

    private static double normCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    private static double normPdf(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2.0 * Math.PI);
    }

    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t
                * Math.exp(-x * x);
        return x < 0 ? -y : y;
    }
}
