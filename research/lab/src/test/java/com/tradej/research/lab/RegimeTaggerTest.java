package com.tradej.research.lab;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegimeTaggerTest {

    @Test
    public void testRegimeTaggerOutputs() {
        List<Candle> candles = new ArrayList<>();
        long time = System.currentTimeMillis();
        // Generate 40 candles with an upward trend to trigger ADX rise
        long lastClose = 100000; // 1000.00 Rs
        for (int i = 0; i < 40; i++) {
            long open = lastClose;
            long high = open + 1000 + (i * 100);
            long low = open - 500;
            long close = open + 800 + (i * 10);
            lastClose = close;
            candles.add(new Candle(
                "SBIN", "1m", time + (i * 60000), time + (i * 60000) + 59999,
                open, high, low, close, 10000, true
            ));
        }

        List<RegimeTagger.RegimeSnapshot> snapshots = RegimeTagger.tagRegimes(candles, 14);
        assertNotNull(snapshots);
        // We need at least period * 2 elements before we get tags
        assertFalse(snapshots.isEmpty(), "Snapshots list should not be empty");

        // Verify that fields are filled
        for (RegimeTagger.RegimeSnapshot snapshot : snapshots) {
            assertNotNull(snapshot.regime());
            assertTrue(snapshot.atr() > 0, "ATR should be positive");
            assertTrue(snapshot.adx() >= 0, "ADX should be non-negative");
        }
    }
}
