package com.tradej.options.calculator;

import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class BlackScholesCalculatorTest {

    @Test
    void callDeltaNearOneForDeepItm() {
        var greeks = BlackScholesCalculator.compute(100, 80, 0.25, 0.2, 0.065, OptionType.CALL);
        assertTrue(greeks.delta() != null && greeks.delta() > 0.9);
    }

    @Test
    void ivSolverConverges() {
        double price = BlackScholesCalculator.price(100, 100, 0.5, 0.25, 0.065, OptionType.CALL);
        double iv = IVSolver.solve(price, 100, 100, 0.5, 0.065, OptionType.CALL);
        assertTrue(Math.abs(iv - 0.25) < 0.05);
    }
}
