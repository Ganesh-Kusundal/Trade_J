package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class BasicIndicatorsTest {

    private static Candle c(long startMs, long open, long high, long low, long close, long volume) {
        return new Candle("RELIANCE", "1m", startMs, startMs + 59_999, open, high, low, close, volume, true);
    }

    private static List<Candle> candles(int count) {
        Candle[] arr = new Candle[count];
        for (int i = 0; i < count; i++) {
            long base = 10000L + i * 50;
            arr[i] = c(i * 60_000L, base, base + 100, base - 50, base + 25, 1000 + i * 10);
        }
        return List.of(arr);
    }

    @Test
    void smaProducesCorrectValues() {
        List<Candle> data = candles(5);
        List<Double> result = new SMA(3).calculate(data);
        assertEquals(5, result.size());
        assertTrue(Double.isNaN(result.get(0)));
        assertTrue(Double.isNaN(result.get(1)));
        assertFalse(Double.isNaN(result.get(2)));
    }

    @Test
    void emaProducesCorrectValues() {
        List<Candle> data = candles(10);
        List<Double> result = new EMA(5).calculate(data);
        assertEquals(10, result.size());
        assertFalse(Double.isNaN(result.get(4)));
        assertFalse(Double.isNaN(result.get(9)));
    }

    @Test
    void rsiBoundedBetween0And100() {
        List<Candle> data = candles(30);
        List<Double> result = new RSI(14).calculate(data);
        assertEquals(30, result.size());
        for (int i = 14; i < result.size(); i++) {
            assertTrue(result.get(i) >= 0 && result.get(i) <= 100,
                    "RSI should be 0-100, got " + result.get(i));
        }
    }

    @Test
    void atrProducesPositiveValues() {
        List<Candle> data = candles(20);
        List<Double> result = new ATR(10).calculate(data);
        assertEquals(20, result.size());
        for (int i = 10; i < result.size(); i++) {
            assertTrue(result.get(i) > 0, "ATR should be positive");
        }
    }

    @Test
    void vwapProducesValues() {
        List<Candle> data = candles(5);
        List<Double> result = new VWAP().calculate(data);
        assertEquals(5, result.size());
        for (double v : result) {
            assertTrue(v > 0, "VWAP should be positive");
        }
    }

    @Test
    void macdProducesPoints() {
        List<Candle> data = candles(40);
        List<MACD.Point> result = new MACD(12, 26, 9).calculate(data);
        assertEquals(40, result.size());
        assertNotNull(result.getLast());
    }

    @Test
    void obvProducesCumulativeValues() {
        List<Candle> data = candles(5);
        List<Long> result = new OBV().calculate(data);
        assertEquals(5, result.size());
        assertNotNull(result.getFirst());
    }

    @Test
    void superTrendProducesPoints() {
        List<Candle> data = candles(20);
        List<SuperTrend.Point> result = new SuperTrend(10, 3.0).calculate(data);
        assertEquals(20, result.size());
    }
}
