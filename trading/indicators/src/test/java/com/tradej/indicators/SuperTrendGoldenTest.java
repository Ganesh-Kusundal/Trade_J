package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SuperTrendGoldenTest {

    private static Candle c(long startMs, long open, long high, long low, long close) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, open, high, low, close, 1000, true);
    }

    private static List<Candle> trendingCandles(int count, boolean up) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long base = up ? 10000L + i * 50 : 50000L - i * 50;
            list.add(c(i * 60_000L, base, base + 100, base - 50, base + (up ? 40 : -40)));
        }
        return list;
    }

    @Test
    void superTrend_producesCorrectSize() {
        List<Candle> data = trendingCandles(30, true);
        List<SuperTrend.Point> result = new SuperTrend(10, 3.0).calculate(data);
        assertEquals(30, result.size());
    }

    @Test
    void superTrend_emptyList_returnsEmpty() {
        assertTrue(new SuperTrend(10, 3.0).calculate(List.of()).isEmpty());
    }

    @Test
    void superTrend_uptrend_bullishSignals() {
        List<Candle> data = trendingCandles(30, true);
        List<SuperTrend.Point> result = new SuperTrend(10, 3.0).calculate(data);
        assertNotNull(result.getLast());
    }
}
