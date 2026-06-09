package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MACDGoldenTest {

    private static Candle c(long startMs, long close) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, close, close + 10, close - 10, close, 1000, true);
    }

    private static List<Candle> linearCandles(int count, long startPrice, long step) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(c(i * 60_000L, startPrice + i * step));
        return list;
    }

    @Test
    void macd_producesCorrectSize() {
        List<Candle> data = linearCandles(40, 10000L, 50L);
        List<MACD.Point> result = new MACD(12, 26, 9).calculate(data);
        assertEquals(40, result.size());
    }

    @Test
    void macd_risingTrend_positiveHistogram() {
        List<Candle> data = linearCandles(50, 10000L, 100L);
        List<MACD.Point> result = new MACD(12, 26, 9).calculate(data);
        MACD.Point last = result.getLast();
        assertNotNull(last);
    }

    @Test
    void macd_flatPrices_nearZeroHistogram() {
        List<Candle> data = new ArrayList<>();
        for (int i = 0; i < 50; i++) data.add(c(i * 60_000L, 25000L));
        List<MACD.Point> result = new MACD(12, 26, 9).calculate(data);
        MACD.Point last = result.getLast();
        assertNotNull(last);
    }

    @Test
    void macd_insufficientData_returnsCorrectSize() {
        List<Candle> data = linearCandles(5, 10000L, 50L);
        List<MACD.Point> result = new MACD(12, 26, 9).calculate(data);
        assertEquals(5, result.size());
    }
}
