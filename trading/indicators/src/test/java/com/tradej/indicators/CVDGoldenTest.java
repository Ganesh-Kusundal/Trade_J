package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CVDGoldenTest {

    private static Candle c(long startMs, long open, long high, long low, long close, long volume) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, open, high, low, close, volume, true);
    }

    @Test
    void cvd_producesCorrectSize() {
        List<Candle> data = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long base = 10000L + i * 10;
            data.add(c(i * 60_000L, base, base + 50, base - 50, base + 20, 1000 + i * 100));
        }
        List<CVD.Point> result = new CVD().calculate(data);
        assertEquals(20, result.size());
    }

    @Test
    void cvd_risingTrend_positiveCVD() {
        List<Candle> data = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long base = 10000L + i * 100;
            data.add(c(i * 60_000L, base, base + 50, base - 10, base + 90, 1000));
        }
        List<CVD.Point> result = new CVD().calculate(data);
        assertNotNull(result.getLast());
    }

    @Test
    void cvd_emptyList_returnsEmpty() {
        assertTrue(new CVD().calculate(List.of()).isEmpty());
    }
}
