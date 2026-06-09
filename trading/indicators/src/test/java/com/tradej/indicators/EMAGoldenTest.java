package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class EMAGoldenTest {

    private static Candle c(long startMs, long close) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, close, close + 10, close - 10, close, 1000, true);
    }

    private static List<Candle> linearCandles(int count, long startPrice, long step) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(c(i * 60_000L, startPrice + i * step));
        }
        return list;
    }

    @Test
    void ema5_knownInput_firstValueIsSMA() {
        List<Candle> data = linearCandles(10, 10000L, 100L);
        List<Double> result = new EMA(5).calculate(data);
        assertEquals(10, result.size());
        assertTrue(Double.isNaN(result.get(3)), "EMA[3] should be NaN for period=5");
        assertFalse(Double.isNaN(result.get(4)), "EMA[4] should be first valid value");

        double expectedSma = (100.0 + 101.0 + 102.0 + 103.0 + 104.0) / 5.0;
        assertEquals(expectedSma, result.get(4), 0.01, "First EMA value should equal SMA");
    }

    @Test
    void ema5_subsequentValues_useSmoothing() {
        List<Candle> data = linearCandles(10, 10000L, 100L);
        List<Double> result = new EMA(5).calculate(data);
        double multiplier = 2.0 / 6.0;
        double expectedEma5 = result.get(4);
        double close6 = 10500.0 / 100.0;
        double expectedEma6 = (close6 - expectedEma5) * multiplier + expectedEma5;
        assertEquals(expectedEma6, result.get(5), 0.01);
    }

    @Test
    void ema_insufficientData_returnsNaN() {
        List<Candle> data = linearCandles(3, 10000L, 100L);
        List<Double> result = new EMA(5).calculate(data);
        assertEquals(3, result.size());
        for (double v : result) assertTrue(Double.isNaN(v));
    }

    @Test
    void ema_emptyList_returnsEmpty() {
        assertTrue(new EMA(5).calculate(List.of()).isEmpty());
    }

    @Test
    void ema_flatPrices_allSameValue() {
        List<Candle> data = new ArrayList<>();
        for (int i = 0; i < 20; i++) data.add(c(i * 60_000L, 50000L));
        List<Double> result = new EMA(10).calculate(data);
        for (int i = 9; i < result.size(); i++) {
            assertEquals(500.0, result.get(i), 0.01, "EMA of flat 50000 paisa = 500.0 price");
        }
    }
}
