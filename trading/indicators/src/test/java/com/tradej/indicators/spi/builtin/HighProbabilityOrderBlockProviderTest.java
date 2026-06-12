package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("unit")
class HighProbabilityOrderBlockProviderTest {

    private static List<Candle> sampleCandles(int n) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long base = 10000L + i * 20;
            candles.add(new Candle(
                    "TEST", "1m",
                    i * 60_000L, i * 60_000L + 59_999,
                    base, base + 100, base - 50, base + 30, 1000, true));
        }
        return candles;
    }

    @Test
    void metadataIsStable() {
        HighProbabilityOrderBlockProvider p = new HighProbabilityOrderBlockProvider();
        assertEquals("order-block", p.name());
        assertEquals("High-Probability Order Block", p.displayName());
        assertEquals(3, p.minPeriod());
    }

    @Test
    void calculateProducesOneValuePerCandle() {
        HighProbabilityOrderBlockProvider p = new HighProbabilityOrderBlockProvider();
        List<Double> result = p.calculate(sampleCandles(10));
        assertNotNull(result);
        assertEquals(10, result.size());
    }
}
