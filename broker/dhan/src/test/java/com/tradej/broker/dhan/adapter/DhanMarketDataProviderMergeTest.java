package com.tradej.broker.dhan.adapter;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanMarketDataProviderMergeTest {

    @Test
    void mergeCandlesDeduplicatesAndSortsByStartTime() {
        Candle a = candle(2000L, 3000L, 101L);
        Candle b = candle(1000L, 2000L, 99L);
        Candle duplicateB = candle(1000L, 2000L, 100L);
        List<Candle> merged = DhanMarketDataProvider.mergeCandles(List.of(a, b, duplicateB));

        assertEquals(2, merged.size());
        assertEquals(1000L, merged.get(0).startTimeMs());
        assertEquals(2000L, merged.get(1).startTimeMs());
        // first duplicate is preserved by merge strategy
        assertEquals(99L, merged.get(0).openPaisa());
    }

    @Test
    void mergeCandlesReturnsEmptyForNullOrEmptyInput() {
        assertTrue(DhanMarketDataProvider.mergeCandles(null).isEmpty());
        assertTrue(DhanMarketDataProvider.mergeCandles(List.of()).isEmpty());
    }

    private static Candle candle(long start, long end, long open) {
        return new Candle("NIFTY", "1d", start, end, open, open + 1, open - 1, open, 10L, true);
    }
}
