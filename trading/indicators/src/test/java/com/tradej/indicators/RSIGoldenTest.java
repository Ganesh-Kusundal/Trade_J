package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class RSIGoldenTest {

    private static Candle c(long startMs, long open, long high, long low, long close, long volume) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, open, high, low, close, volume, true);
    }

    private static List<Candle> risingCandles(int count) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long base = 10000L + i * 100;
            list.add(c(i * 60_000L, base, base + 50, base - 10, base + 90, 1000));
        }
        return list;
    }

    private static List<Candle> fallingCandles(int count) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long base = 50000L - i * 100;
            list.add(c(i * 60_000L, base, base + 10, base - 50, base - 90, 1000));
        }
        return list;
    }

    private static List<Candle> flatCandles(int count) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(c(i * 60_000L, 10000L, 10000L, 10000L, 10000L, 1000));
        }
        return list;
    }

    @Test
    void rsi14_knownInput_boundedBetween0And100() {
        List<Candle> data = risingCandles(30);
        List<Double> result = new RSI(14).calculate(data);
        assertEquals(30, result.size());
        for (int i = 14; i < result.size(); i++) {
            assertTrue(result.get(i) >= 0 && result.get(i) <= 100,
                    "RSI[" + i + "] should be 0-100, got " + result.get(i));
        }
    }

    @Test
    void rsi14_allGreen_returnsNear100() {
        List<Candle> data = risingCandles(30);
        List<Double> result = new RSI(14).calculate(data);
        assertTrue(result.get(29) > 80,
                "All rising candles should produce RSI > 80, got " + result.get(29));
    }

    @Test
    void rsi14_allRed_returnsNear0() {
        List<Candle> data = fallingCandles(30);
        List<Double> result = new RSI(14).calculate(data);
        assertTrue(result.get(29) < 20,
                "All falling candles should produce RSI < 20, got " + result.get(29));
    }

    @Test
    void rsi14_flatPrices_highRSI() {
        List<Candle> data = flatCandles(30);
        List<Double> result = new RSI(14).calculate(data);
        for (int i = 14; i < result.size(); i++) {
            assertTrue(result.get(i) > 95 || Double.isNaN(result.get(i)),
                    "Flat prices produce RSI ~99 (zero loss → rs=100), got " + result.get(i));
        }
    }

    @Test
    void rsi14_insufficientData_returnsNaN() {
        List<Candle> data = risingCandles(10);
        List<Double> result = new RSI(14).calculate(data);
        assertEquals(10, result.size());
        for (int i = 0; i < Math.min(14, result.size()); i++) {
            assertTrue(Double.isNaN(result.get(i)),
                    "RSI[" + i + "] should be NaN for insufficient data");
        }
    }

    @Test
    void rsi14_emptyList_returnsEmpty() {
        List<Double> result = new RSI(14).calculate(List.of());
        assertTrue(result.isEmpty());
    }
}
