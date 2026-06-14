package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("unit")
class SuperTrendProviderTest {

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
        SuperTrendProvider p = new SuperTrendProvider();
        assertEquals("supertrend", p.name());
        assertEquals("SuperTrend", p.displayName());
        assertEquals(11, p.minPeriod());
    }

    @Test
    void calculateProducesOneValuePerCandle() {
        SuperTrendProvider p = new SuperTrendProvider();
        List<Double> result = p.calculate(sampleCandles(20));
        assertNotNull(result);
        assertEquals(20, result.size());
    }
}
