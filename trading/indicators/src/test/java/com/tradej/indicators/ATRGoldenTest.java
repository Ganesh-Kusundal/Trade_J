package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ATRGoldenTest {

    private static Candle c(long startMs, long high, long low, long prevClose) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, prevClose, high, low, prevClose, 1000, true);
    }

    private static List<Candle> volatileCandles(int count) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long base = 10000L + i * 10;
            list.add(c(i * 60_000L, base + 200, base - 100, base));
        }
        return list;
    }

    @Test
    void atr10_positiveValues() {
        List<Candle> data = volatileCandles(20);
        List<Double> result = new ATR(10).calculate(data);
        assertEquals(20, result.size());
        for (int i = 10; i < result.size(); i++) {
            assertTrue(result.get(i) > 0, "ATR should be positive, got " + result.get(i) + " at [" + i + "]");
        }
    }

    @Test
    void atr_insufficientData_returnsNaN() {
        List<Candle> data = volatileCandles(5);
        List<Double> result = new ATR(10).calculate(data);
        assertEquals(5, result.size());
    }

    @Test
    void atr_emptyList_returnsEmpty() {
        assertTrue(new ATR(10).calculate(List.of()).isEmpty());
    }

    @Test
    void atr_higherVolatility_higherATR() {
        List<Candle> calm = new ArrayList<>();
        List<Candle> wild = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long base = 10000L;
            calm.add(c(i * 60_000L, base + 10, base - 10, base));
            wild.add(c(i * 60_000L, base + 500, base - 500, base));
        }
        List<Double> calmResult = new ATR(10).calculate(calm);
        List<Double> wildResult = new ATR(10).calculate(wild);
        assertTrue(wildResult.get(19) > calmResult.get(19),
                "Higher volatility should produce higher ATR");
    }
}
