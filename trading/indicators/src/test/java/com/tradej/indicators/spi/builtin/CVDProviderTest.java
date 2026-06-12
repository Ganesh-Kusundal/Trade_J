package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("unit")
class CVDProviderTest {

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
        CVDProvider p = new CVDProvider();
        assertEquals("cvd", p.name());
        assertEquals("Cumulative Volume Delta", p.displayName());
        assertEquals(1, p.minPeriod());
    }

    @Test
    void calculateProducesOneValuePerCandle() {
        CVDProvider p = new CVDProvider();
        List<Double> result = p.calculate(sampleCandles(10));
        assertNotNull(result);
        assertEquals(10, result.size());
    }
}
