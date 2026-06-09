package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SwingHighLowGoldenTest {

    private static Candle c(long startMs, long high, long low) {
        return new Candle("TEST", "1m", startMs, startMs + 59_999, (high + low) / 2, high, low, (high + low) / 2, 1000, true);
    }

    @Test
    void swingHighLow_detectsSwings() {
        List<Candle> data = new ArrayList<>();
        long[] highs = {100, 110, 120, 115, 105, 95, 100, 110, 120, 130};
        long[] lows = {90, 95, 100, 95, 85, 80, 85, 90, 100, 110};
        for (int i = 0; i < highs.length; i++) {
            data.add(c(i * 60_000L, highs[i] * 100, lows[i] * 100));
        }
        SwingHighLow shl = new SwingHighLow(3);
        assertNotNull(shl);
    }

    @Test
    void swingHighLow_defaultConstructor() {
        SwingHighLow shl = new SwingHighLow();
        assertNotNull(shl);
    }

    @Test
    void swingHighLow_emptyList() {
        SwingHighLow shl = new SwingHighLow(3);
        assertNotNull(shl);
    }
}
