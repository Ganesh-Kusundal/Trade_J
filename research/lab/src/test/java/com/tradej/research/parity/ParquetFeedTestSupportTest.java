package com.tradej.research.parity;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParquetFeedTestSupportTest {

    @TempDir
    Path tempDir;

    @Test
    public void writeBars_thenQuery_returnsSameData() throws Exception {
        long baseTime = 1717146000000L;
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long startTimeMs = baseTime + (i * 60000L);
            candles.add(new Candle(
                    "TEST",
                    "1m",
                    startTimeMs,
                    startTimeMs + 59999L,
                    100000L + (i * 100),
                    102000L + (i * 100),
                    99000L + (i * 100),
                    101000L + (i * 100),
                    5000L,
                    true
            ));
        }

        try (ParquetFeedTestSupport feed = new ParquetFeedTestSupport(tempDir)) {
            int written = feed.writeCandles("NSE_EQ", "TEST", "1m", candles);
            assertEquals(5, written, "All 5 candles should be written");

            List<Map<String, Object>> rows = feed.engine().queryEquityCandles(
                    "TEST", baseTime, baseTime + (5L * 60000L), 100);
            assertEquals(5, rows.size(), "Engine should return all 5 candles");

            for (int i = 0; i < 5; i++) {
                Map<String, Object> row = rows.get(i);
                assertEquals("TEST", row.get("symbol"));
                assertEquals("1m", row.get("interval"));
                assertEquals(baseTime + (i * 60000L), ((Number) row.get("barTimeMs")).longValue());
                assertEquals(100000L + (i * 100L), ((Number) row.get("openPaisa")).longValue());
                assertEquals(102000L + (i * 100L), ((Number) row.get("highPaisa")).longValue());
                assertEquals(99000L + (i * 100L), ((Number) row.get("lowPaisa")).longValue());
                assertEquals(101000L + (i * 100L), ((Number) row.get("closePaisa")).longValue());
                assertEquals(5000L, ((Number) row.get("volume")).longValue());
            }
        }
    }

    @Test
    public void tempDir_isolatedAcrossTests() throws Exception {
        long baseTime = 1717146000000L;
        Path aDir = tempDir.resolve("feedA");
        Path bDir = tempDir.resolve("feedB");

        List<Candle> candlesA = List.of(new Candle(
                "AAA", "1m", baseTime, baseTime + 59999L,
                100L, 110L, 90L, 105L, 1000L, true));
        List<Candle> candlesB = List.of(new Candle(
                "BBB", "1m", baseTime, baseTime + 59999L,
                200L, 210L, 190L, 205L, 2000L, true));

        try (ParquetFeedTestSupport feedA = new ParquetFeedTestSupport(aDir);
             ParquetFeedTestSupport feedB = new ParquetFeedTestSupport(bDir)) {

            assertNotEquals(feedA.equityRoot(), feedB.equityRoot(),
                    "Each helper resolves its own equity root under its temp dir");

            feedA.writeCandles("NSE_EQ", "AAA", "1m", candlesA);
            feedB.writeCandles("NSE_EQ", "BBB", "1m", candlesB);

            List<Map<String, Object>> aOwn = feedA.engine().queryEquityCandles(
                    "AAA", baseTime, baseTime + 60000L, 100);
            List<Map<String, Object>> bOwn = feedB.engine().queryEquityCandles(
                    "BBB", baseTime, baseTime + 60000L, 100);
            assertEquals(1, aOwn.size(), "Feed A should see its own AAA candle");
            assertEquals(1, bOwn.size(), "Feed B should see its own BBB candle");

            assertTrue(feedA.engine().queryEquityCandles("BBB", baseTime, baseTime + 60000L, 100).isEmpty(),
                    "Feed A must not see Feed B's data (different temp dir)");
            assertTrue(feedB.engine().queryEquityCandles("AAA", baseTime, baseTime + 60000L, 100).isEmpty(),
                    "Feed B must not see Feed A's data (different temp dir)");
        }
    }
}
