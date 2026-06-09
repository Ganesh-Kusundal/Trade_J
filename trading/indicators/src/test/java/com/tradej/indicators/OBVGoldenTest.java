package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OBVGoldenTest {

    private static Candle c(long startMs, long close, long volume) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, close, close + 10, close - 10, close, volume, true);
    }

    @Test
    void obv_risingPrices_cumulativeIncrease() {
        List<Candle> data = List.of(
                c(0, 10000, 1000),
                c(1, 10100, 2000),
                c(2, 10200, 1500),
                c(3, 10300, 3000)
        );
        List<Long> result = new OBV().calculate(data);
        assertEquals(4, result.size());
        assertTrue(result.get(3) > result.get(0), "OBV should increase with rising prices");
    }

    @Test
    void obv_fallingPrices_cumulativeDecrease() {
        List<Candle> data = List.of(
                c(0, 10300, 1000),
                c(1, 10200, 2000),
                c(2, 10100, 1500),
                c(3, 10000, 3000)
        );
        List<Long> result = new OBV().calculate(data);
        assertEquals(4, result.size());
        assertTrue(result.get(3) < result.get(0), "OBV should decrease with falling prices");
    }

    @Test
    void obv_emptyList_returnsEmpty() {
        assertTrue(new OBV().calculate(List.of()).isEmpty());
    }
}
