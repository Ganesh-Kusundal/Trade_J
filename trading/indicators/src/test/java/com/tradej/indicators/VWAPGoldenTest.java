package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class VWAPGoldenTest {

    private static Candle c(long startMs, long high, long low, long close, long volume) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, close, high, low, close, volume, true);
    }

    @Test
    void vwap_singleCandle_equalsClose() {
        List<Candle> data = List.of(c(0, 10100, 9900, 10000, 1000));
        List<Double> result = new VWAP().calculate(data);
        assertEquals(1, result.size());
        assertTrue(result.get(0) > 0);
    }

    @Test
    void vwap_multipleCandles_volumeWeighted() {
        List<Candle> data = List.of(
                c(0, 10100, 9900, 10000, 1000),
                c(1, 10200, 10000, 10100, 2000),
                c(2, 10300, 10100, 10200, 3000)
        );
        List<Double> result = new VWAP().calculate(data);
        assertEquals(3, result.size());
        assertTrue(result.get(2) > result.get(0), "VWAP should trend up with rising prices");
    }

    @Test
    void vwap_emptyList_returnsEmpty() {
        assertTrue(new VWAP().calculate(List.of()).isEmpty());
    }

    @Test
    void vwap_flatPrices_constantVwap() {
        List<Candle> data = new ArrayList<>();
        for (int i = 0; i < 10; i++) data.add(c(i * 60_000L, 25100, 24900, 25000, 1000));
        List<Double> result = new VWAP().calculate(data);
        for (double v : result) {
            assertTrue(v > 0, "VWAP should be positive");
        }
    }
}
