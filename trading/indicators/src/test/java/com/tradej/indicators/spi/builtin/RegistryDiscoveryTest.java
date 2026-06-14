package com.tradej.indicators.spi.builtin;

import com.tradej.indicators.spi.IndicatorRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class RegistryDiscoveryTest {

    @Test
    void discoversAllRegisteredCandleIndicators() {
        IndicatorRegistry registry = IndicatorRegistry.discover();

        // 6 simple candle indicators plus 5 typed candle indicators are
        // registered in META-INF/services. The 5 typed indicators
        // (HalfTrend, CVD, BollingerSqueeze, SwingHighLow,
        // HighProbabilityOrderBlock) override calculateTyped() so the engine
        // can route through the registry without losing record fidelity.
        // The other un-registered source classes (SuperTrendProvider,
        // HeikinAshiProvider) remain a deliberate scope gap.
        assertEquals(11, registry.size(), () ->
                "Expected 11 registered candle indicators, got " + registry.size()
                        + " — " + registry.names());

        for (String name : new String[]{
                "rsi", "ema", "sma", "atr", "vwap", "obv",
                "halftrend", "cvd", "bollinger-squeeze", "swing-high-low", "order-block"
        }) {
            assertTrue(registry.get(name).isPresent(), () -> "Missing candle indicator: " + name);
        }

        // The two non-candle indicators must NOT appear in the candle registry.
        assertFalse(registry.get("volume-profile").isPresent(),
                "volume-profile must be registered only via NonCandleIndicatorProvider");
        assertFalse(registry.get("tick-level-cvd").isPresent(),
                "tick-level-cvd must be registered only via NonCandleIndicatorProvider");
    }
}
