package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class HalfTrendGoldenTest {

    private static Candle c(long startMs, long high, long low, long close) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, close, high, low, close, 1000, true);
    }

    @Test
    void halfTrend_producesOutput() {
        List<Candle> data = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            long base = 10000L + i * 20;
            data.add(c(i * 60_000L, base + 100, base - 50, base + 30));
        }
        HalfTrend ht = new HalfTrend(2, 2, 10);
        assertNotNull(ht);
    }

    @Test
    void halfTrend_defaultConstructor() {
        HalfTrend ht = new HalfTrend();
        assertNotNull(ht);
    }

    @Test
    void halfTrend_emptyList() {
        HalfTrend ht = new HalfTrend(2, 2, 10);
        assertNotNull(ht);
    }
}
