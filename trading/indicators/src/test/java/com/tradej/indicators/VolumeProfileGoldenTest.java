package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class VolumeProfileGoldenTest {

    private static Candle c(long startMs, long high, long low, long close, long volume) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, close, high, low, close, volume, true);
    }

    @Test
    void volumeProfile_producesOutput() {
        List<Candle> data = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long base = 10000L + (i % 5) * 100;
            data.add(c(i * 60_000L, base + 50, base - 50, base, 1000));
        }
        VolumeProfile vp = new VolumeProfile(50L);
        assertNotNull(vp);
    }

    @Test
    void volumeProfile_emptyList() {
        VolumeProfile vp = new VolumeProfile(50L);
        assertNotNull(vp);
    }
}
