package com.tradej.options.calculator;

import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BlackScholesCalculatorTest {

    private static final double TOLERANCE = 0.01;

    @Test
    void callDeltaNearOneForDeepItm() {
        OptionGreeks greeks = BlackScholesCalculator.compute(100, 80, 0.25, 0.2, 0.065, OptionType.CALL);
        assertNotNull(greeks.delta());
        assertTrue(greeks.delta() > 0.9, "Deep ITM call delta should be near 1.0, got " + greeks.delta());
    }

    @Test
    void deepOtmCall_deltaNearZero() {
        OptionGreeks greeks = BlackScholesCalculator.compute(100, 150, 0.25, 0.2, 0.065, OptionType.CALL);
        assertNotNull(greeks.delta());
        assertTrue(greeks.delta() < 0.1, "Deep OTM call delta should be near 0, got " + greeks.delta());
    }

    @Test
    void deepItmPut_deltaNearNegativeOne() {
        OptionGreeks greeks = BlackScholesCalculator.compute(100, 130, 0.25, 0.2, 0.065, OptionType.PUT);
        assertNotNull(greeks.delta());
        assertTrue(greeks.delta() < -0.9, "Deep ITM put delta should be near -1.0, got " + greeks.delta());
    }

    @Test
    void atTheMoney_deltaNearHalf() {
        OptionGreeks greeks = BlackScholesCalculator.compute(100, 100, 0.5, 0.2, 0.065, OptionType.CALL);
        assertNotNull(greeks.delta());
        assertTrue(Math.abs(greeks.delta() - 0.55) < 0.15,
                "ATM call delta should be near 0.55 (drift-adjusted), got " + greeks.delta());
    }

    @Test
    void nearExpiry_highGamma() {
        OptionGreeks nearExpiry = BlackScholesCalculator.compute(100, 100, 0.01, 0.2, 0.065, OptionType.CALL);
        OptionGreeks farExpiry = BlackScholesCalculator.compute(100, 100, 1.0, 0.2, 0.065, OptionType.CALL);
        assertNotNull(nearExpiry.gamma());
        assertNotNull(farExpiry.gamma());
        assertTrue(nearExpiry.gamma() > farExpiry.gamma(),
                "Gamma should be higher near expiry for ATM options");
    }

    @Test
    void zeroVolatility_intrinsicValue() {
        double callPrice = BlackScholesCalculator.price(110, 100, 0.5, 0.0, 0.065, OptionType.CALL);
        assertEquals(10.0, callPrice, 0.5, "Zero vol call should be intrinsic value (S-K)");

        double putPrice = BlackScholesCalculator.price(90, 100, 0.5, 0.0, 0.065, OptionType.PUT);
        assertEquals(10.0, putPrice, 0.5, "Zero vol put should be intrinsic value (K-S)");

        double otmCall = BlackScholesCalculator.price(90, 100, 0.5, 0.0, 0.065, OptionType.CALL);
        assertEquals(0.0, otmCall, 0.5, "OTM call with zero vol should be 0");

        double otmPut = BlackScholesCalculator.price(110, 100, 0.5, 0.0, 0.065, OptionType.PUT);
        assertEquals(0.0, otmPut, 0.5, "OTM put with zero vol should be 0");
    }

    @Test
    void zeroTimeToExpiry_intrinsicValue() {
        double callPrice = BlackScholesCalculator.price(110, 100, 0.0, 0.25, 0.065, OptionType.CALL);
        assertEquals(10.0, callPrice, 0.5, "At expiry, call = max(S-K, 0)");

        double otmCall = BlackScholesCalculator.price(90, 100, 0.0, 0.25, 0.065, OptionType.CALL);
        assertEquals(0.0, otmCall, 0.5, "OTM call at expiry should be 0");
    }

    @Test
    void putCallParity_holds() {
        double spot = 100, strike = 100, tte = 0.5, vol = 0.25, rate = 0.065;
        double callPrice = BlackScholesCalculator.price(spot, strike, tte, vol, rate, OptionType.CALL);
        double putPrice = BlackScholesCalculator.price(spot, strike, tte, vol, rate, OptionType.PUT);

        double pvStrike = strike * Math.exp(-rate * tte);
        double parityDiff = Math.abs((callPrice - putPrice) - (spot - pvStrike));
        assertTrue(parityDiff < 0.01,
                "Put-call parity: C - P = S - K*e^(-rT). Diff=" + parityDiff);
    }

    @Test
    void vegaPositive_forAllOptionTypes() {
        OptionGreeks callGreeks = BlackScholesCalculator.compute(100, 100, 0.5, 0.2, 0.065, OptionType.CALL);
        OptionGreeks putGreeks = BlackScholesCalculator.compute(100, 100, 0.5, 0.2, 0.065, OptionType.PUT);
        assertNotNull(callGreeks.vega());
        assertNotNull(putGreeks.vega());
        assertTrue(callGreeks.vega() > 0, "Call vega should be positive");
        assertTrue(putGreeks.vega() > 0, "Put vega should be positive");
    }

    @Test
    void thetaNegative_forLongOptions() {
        OptionGreeks callGreeks = BlackScholesCalculator.compute(100, 100, 0.5, 0.2, 0.065, OptionType.CALL);
        OptionGreeks putGreeks = BlackScholesCalculator.compute(100, 100, 0.5, 0.2, 0.065, OptionType.PUT);
        assertNotNull(callGreeks.theta());
        assertNotNull(putGreeks.theta());
        assertTrue(callGreeks.theta() < 0, "Call theta should be negative (time decay)");
        assertTrue(putGreeks.theta() < 0, "Put theta should be negative (time decay)");
    }

    @Test
    void gammaPositive_forAllOptionTypes() {
        OptionGreeks callGreeks = BlackScholesCalculator.compute(100, 100, 0.5, 0.2, 0.065, OptionType.CALL);
        OptionGreeks putGreeks = BlackScholesCalculator.compute(100, 100, 0.5, 0.2, 0.065, OptionType.PUT);
        assertNotNull(callGreeks.gamma());
        assertNotNull(putGreeks.gamma());
        assertTrue(callGreeks.gamma() > 0, "Call gamma should be positive");
        assertTrue(putGreeks.gamma() > 0, "Put gamma should be positive");
    }

    @Test
    void price_monotonicInSpot() {
        double p1 = BlackScholesCalculator.price(90, 100, 0.5, 0.2, 0.065, OptionType.CALL);
        double p2 = BlackScholesCalculator.price(100, 100, 0.5, 0.2, 0.065, OptionType.CALL);
        double p3 = BlackScholesCalculator.price(110, 100, 0.5, 0.2, 0.065, OptionType.CALL);
        assertTrue(p1 < p2 && p2 < p3,
                "Call price should increase with spot: " + p1 + " < " + p2 + " < " + p3);
    }

    @Test
    void ivSolver_convergesForVariousMoneyness() {
        double spot = 100, vol = 0.25, tte = 0.5, rate = 0.065;
        for (double strike : new double[]{80, 90, 100, 110, 120}) {
            double price = BlackScholesCalculator.price(spot, strike, tte, vol, rate, OptionType.CALL);
            double solvedIv = IVSolver.solve(price, spot, strike, tte, rate, OptionType.CALL);
            assertTrue(Math.abs(solvedIv - vol) < 0.05,
                    "IV solver should converge near " + vol + " for strike=" + strike + ", got " + solvedIv);
        }
    }

    @Test
    void unknownGreeks_whenInvalidInputs() {
        OptionGreeks greeks = BlackScholesCalculator.compute(100, 100, 0.0, 0.0, 0.065, OptionType.CALL);
        assertEquals(OptionGreeks.UNKNOWN, greeks, "Zero vol and zero time should return UNKNOWN");
    }

    @Test
    void ivSolverConverges() {
        double price = BlackScholesCalculator.price(100, 100, 0.5, 0.25, 0.065, OptionType.CALL);
        double iv = IVSolver.solve(price, 100, 100, 0.5, 0.065, OptionType.CALL);
        assertTrue(Math.abs(iv - 0.25) < 0.05);
    }
}
