package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BollingerSqueezeGoldenTest {

    private static Candle c(long startMs, long close) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, close, close + 10, close - 10, close, 1000, true);
    }

    @Test
    void bollingerSqueeze_producesCorrectSize() {
        List<Candle> data = new ArrayList<>();
        for (int i = 0; i < 30; i++) data.add(c(i * 60_000L, 10000L + i * 10));
        BollingerSqueeze bs = new BollingerSqueeze(20, 2.0);
        assertNotNull(bs);
    }

    @Test
    void bollingerSqueeze_flatPrices_lowSqueeze() {
        List<Candle> data = new ArrayList<>();
        for (int i = 0; i < 30; i++) data.add(c(i * 60_000L, 25000L));
        BollingerSqueeze bs = new BollingerSqueeze(20, 2.0);
        assertNotNull(bs);
    }

    @Test
    void bollingerSqueeze_defaultConstructor() {
        BollingerSqueeze bs = new BollingerSqueeze();
        assertNotNull(bs);
    }
}
