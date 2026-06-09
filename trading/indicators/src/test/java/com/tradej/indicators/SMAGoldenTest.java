package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SMAGoldenTest {

    private static Candle c(long startMs, long close) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, close, close + 10, close - 10, close, 1000, true);
    }

    @Test
    void sma3_knownInput_exactValues() {
        List<Candle> data = List.of(
                c(0, 10000L), c(1, 20000L), c(2, 30000L), c(3, 40000L), c(4, 50000L));
        List<Double> result = new SMA(3).calculate(data);
        assertEquals(5, result.size());
        assertTrue(Double.isNaN(result.get(0)));
        assertTrue(Double.isNaN(result.get(1)));
        assertEquals(200.0, result.get(2), 0.01, "SMA[2] = (100+200+300)/3 in price");
        assertEquals(300.0, result.get(3), 0.01, "SMA[3] = (200+300+400)/3 in price");
        assertEquals(400.0, result.get(4), 0.01, "SMA[4] = (300+400+500)/3 in price");
    }

    @Test
    void sma_emptyList_returnsEmpty() {
        assertTrue(new SMA(5).calculate(List.of()).isEmpty());
    }

    @Test
    void sma_flatPrices_constantValue() {
        List<Candle> data = new ArrayList<>();
        for (int i = 0; i < 10; i++) data.add(c(i * 60_000L, 25000L));
        List<Double> result = new SMA(3).calculate(data);
        for (int i = 2; i < result.size(); i++) {
            assertEquals(250.0, result.get(i), 0.01, "SMA of flat 25000 paisa = 250.0 price");
        }
    }
}
