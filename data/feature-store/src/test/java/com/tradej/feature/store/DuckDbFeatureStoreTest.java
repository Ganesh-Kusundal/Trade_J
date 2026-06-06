package com.tradej.feature.store;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.FeatureVector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DuckDbFeatureStoreTest {

    private DuckDbFeatureStore store;

    @BeforeEach
    void setUp() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:duckdb:");
        store = new DuckDbFeatureStore(conn);
    }

    @Test
    void feedsAndRetrievesFeatures() {
        feedCandle("SBIN", "5m", 1_710_000_000_000L, 1_710_000_300_000L, 75_000L, 75_500L, 74_800L, 75_200L, 10_000L);
        feedCandle("SBIN", "5m", 1_710_000_300_000L, 1_710_000_600_000L, 75_200L, 75_800L, 75_100L, 75_600L, 15_000L);
        feedCandle("SBIN", "5m", 1_710_000_600_000L, 1_710_000_900_000L, 75_600L, 76_200L, 75_400L, 76_000L, 12_000L);
        feedCandle("SBIN", "5m", 1_710_000_900_000L, 1_710_001_200_000L, 76_000L, 76_500L, 75_900L, 76_300L, 18_000L);
        feedCandle("SBIN", "5m", 1_710_001_200_000L, 1_710_001_500_000L, 76_300L, 76_800L, 76_100L, 76_500L, 14_000L);
        feedCandle("SBIN", "5m", 1_710_001_500_000L, 1_710_001_800_000L, 76_500L, 77_000L, 76_300L, 76_800L, 20_000L);

        Optional<FeatureVector> result = store.getFeatures("SBIN", "5m", 14);

        assertTrue(result.isPresent(), "Feature vector should be present with 6 candles");
        FeatureVector fv = result.get();

        assertEquals("SBIN", fv.symbol());
        assertEquals("5m", fv.interval());
        assertTrue(fv.rsi() > 0 && fv.rsi() < 100, "RSI should be in range 0-100: " + fv.rsi());
        assertTrue(fv.ema9Paisa() > 0, "EMA9 should be positive");
        assertTrue(fv.ema5Paisa() > 0, "EMA5 should be positive");
        assertTrue(fv.ema21Paisa() > 0, "EMA21 should be positive");
        assertTrue(fv.sma20Paisa() > 0, "SMA20 should be positive");
        assertTrue(fv.sma50Paisa() > 0, "SMA50 should be positive");
        assertTrue(fv.vwapPaisa() > 0, "VWAP should be positive");
    }

    @Test
    void returnsEmptyForInsufficientData() {
        feedCandle("SBIN", "5m", 1_710_000_000_000L, 1_710_000_300_000L, 75_000L, 75_500L, 74_800L, 75_200L, 10_000L);
        Optional<FeatureVector> result = store.getFeatures("SBIN", "5m", 14);
        assertFalse(result.isPresent(), "Should be empty with only 1 candle");
    }

    @Test
    void ingestsMarketTickEvent() {
        store.feed(new MarketTickEvent(EventMetadata.root(), 0L, "RELIANCE", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 250_000L, 100L, 1_000_000L, 1_710_000_000_000L, Optional.empty(), 0L, 0L));
        feedCandle("RELIANCE", "1d", 1_710_000_000_000L, 1_710_086_400_000L, 248_000L, 252_000L, 247_000L, 250_000L, 500_000L);
        feedCandle("RELIANCE", "1d", 1_710_086_400_000L, 1_710_172_800_000L, 250_000L, 253_000L, 249_000L, 252_000L, 600_000L);
        feedCandle("RELIANCE", "1d", 1_710_172_800_000L, 1_710_259_200_000L, 252_000L, 255_000L, 251_000L, 254_000L, 450_000L);
        feedCandle("RELIANCE", "1d", 1_710_259_200_000L, 1_710_345_600_000L, 254_000L, 258_000L, 253_000L, 257_000L, 700_000L);
        feedCandle("RELIANCE", "1d", 1_710_345_600_000L, 1_710_432_000_000L, 257_000L, 260_000L, 256_000L, 259_000L, 550_000L);
        Optional<FeatureVector> result = store.getFeatures("RELIANCE", "1d", 14);
        assertTrue(result.isPresent(), "Should compute features after feeding ticks + candles");
        assertTrue(result.get().rsi() > 0);
    }

    @Test
    void candleDevelopingUpsertsExistingCandle() {
        feedCandleDev("SBIN", "5m", 1_710_000_000_000L, 1_710_000_300_000L, 75_000L, 75_500L, 74_800L, 75_000L, 5_000L);
        feedCandleDev("SBIN", "5m", 1_710_000_000_000L, 1_710_000_300_000L, 75_000L, 75_600L, 74_800L, 75_300L, 10_000L);
        feedCandle("SBIN", "5m", 1_710_000_000_000L, 1_710_000_300_000L, 75_000L, 75_600L, 74_800L, 75_300L, 10_000L);
        feedCandle("SBIN", "5m", 1_710_000_300_000L, 1_710_000_600_000L, 75_300L, 75_800L, 75_100L, 75_600L, 15_000L);
        feedCandle("SBIN", "5m", 1_710_000_600_000L, 1_710_000_900_000L, 75_600L, 76_200L, 75_400L, 76_000L, 12_000L);
        feedCandle("SBIN", "5m", 1_710_000_900_000L, 1_710_001_200_000L, 76_000L, 76_500L, 75_900L, 76_300L, 18_000L);
        feedCandle("SBIN", "5m", 1_710_001_200_000L, 1_710_001_500_000L, 76_300L, 76_800L, 76_100L, 76_500L, 14_000L);
        Optional<FeatureVector> result = store.getFeatures("SBIN", "5m", 14);
        assertTrue(result.isPresent(), "Upserted candle should be overwritten with latest values");
        assertTrue(result.get().vwapPaisa() > 0);
    }

    @Test
    void differentSymbolsAreIndependent() {
        feedCandle("SBIN", "5m", 1_710_000_000_000L, 1_710_000_300_000L, 75_000L, 75_500L, 74_800L, 75_200L, 10_000L);
        feedCandle("SBIN", "5m", 1_710_000_300_000L, 1_710_000_600_000L, 75_200L, 75_800L, 75_100L, 75_600L, 15_000L);
        feedCandle("SBIN", "5m", 1_710_000_600_000L, 1_710_000_900_000L, 75_600L, 76_200L, 75_400L, 76_000L, 12_000L);
        feedCandle("SBIN", "5m", 1_710_000_900_000L, 1_710_001_200_000L, 76_000L, 76_500L, 75_900L, 76_300L, 18_000L);
        feedCandle("SBIN", "5m", 1_710_001_200_000L, 1_710_001_500_000L, 76_300L, 76_800L, 76_100L, 76_500L, 14_000L);
        feedCandle("SBIN", "5m", 1_710_001_500_000L, 1_710_001_800_000L, 76_500L, 77_000L, 76_300L, 76_800L, 20_000L);
        feedCandle("RELIANCE", "5m", 1_710_000_000_000L, 1_710_000_300_000L, 2500_00L, 2510_00L, 2490_00L, 2505_00L, 100_000L);
        assertTrue(store.getFeatures("SBIN", "5m", 14).isPresent(), "SBIN should have features");
        assertFalse(store.getFeatures("RELIANCE", "5m", 14).isPresent(), "RELIANCE should not have enough data");
    }

    @Test
    void handlesMultipleIntervals() {
        feedCandle("SBIN", "5m", 1_710_000_000_000L, 1_710_000_300_000L, 75_000L, 75_500L, 74_800L, 75_200L, 10_000L);
        feedCandle("SBIN", "5m", 1_710_000_300_000L, 1_710_000_600_000L, 75_200L, 75_800L, 75_100L, 75_600L, 15_000L);
        feedCandle("SBIN", "5m", 1_710_000_600_000L, 1_710_000_900_000L, 75_600L, 76_200L, 75_400L, 76_000L, 12_000L);
        feedCandle("SBIN", "5m", 1_710_000_900_000L, 1_710_001_200_000L, 76_000L, 76_500L, 75_900L, 76_300L, 18_000L);
        feedCandle("SBIN", "5m", 1_710_001_200_000L, 1_710_001_500_000L, 76_300L, 76_800L, 76_100L, 76_500L, 14_000L);
        feedCandle("SBIN", "5m", 1_710_001_500_000L, 1_710_001_800_000L, 76_500L, 77_000L, 76_300L, 76_800L, 20_000L);
        feedCandle("SBIN", "1d", 1_710_000_000_000L, 1_710_086_400_000L, 74_000L, 76_500L, 73_500L, 76_000L, 500_000L);
        feedCandle("SBIN", "1d", 1_710_086_400_000L, 1_710_172_800_000L, 76_000L, 77_500L, 75_500L, 77_000L, 600_000L);
        feedCandle("SBIN", "1d", 1_710_172_800_000L, 1_710_259_200_000L, 77_000L, 78_000L, 76_500L, 77_500L, 450_000L);
        feedCandle("SBIN", "1d", 1_710_259_200_000L, 1_710_345_600_000L, 77_500L, 79_000L, 77_000L, 78_500L, 700_000L);
        feedCandle("SBIN", "1d", 1_710_345_600_000L, 1_710_432_000_000L, 78_500L, 80_000L, 78_000L, 79_500L, 550_000L);
        Optional<FeatureVector> fv5m = store.getFeatures("SBIN", "5m", 14);
        Optional<FeatureVector> fv1d = store.getFeatures("SBIN", "1d", 14);
        assertTrue(fv5m.isPresent(), "5m features should be present");
        assertTrue(fv1d.isPresent(), "1d features should be present");
        assertNotEquals(fv5m.get().vwapPaisa(), fv1d.get().vwapPaisa(), "VWAP should differ between intervals");
    }

    @Test
    void rsiOnUpwardTrendIsBullish() {
        long base = 100_000L;
        for (int i = 0; i < 15; i++) {
            long open = base + (i * 500L);
            long close = base + (i * 500L) + 200L;
            feedCandle("SBIN", "5m", 1_710_000_000_000L + (i * 300_000L), 1_710_000_000_000L + (i * 300_000L) + 300_000L,
                    open, close + 100L, open - 50L, close, 10_000L);
        }
        Optional<FeatureVector> fv = store.getFeatures("SBIN", "5m", 14);
        assertTrue(fv.isPresent());
        assertTrue(fv.get().rsi() > 50, "RSI should be bullish (>50) in an upward trend: " + fv.get().rsi());
    }

    @Test
    void rsiOnDownwardTrendIsBearish() {
        long base = 100_000L;
        for (int i = 0; i < 15; i++) {
            long open = base - (i * 500L);
            long close = base - (i * 500L) - 200L;
            feedCandle("SBIN", "5m", 1_710_000_000_000L + (i * 300_000L), 1_710_000_000_000L + (i * 300_000L) + 300_000L,
                    open, open + 50L, close - 100L, close, 10_000L);
        }
        Optional<FeatureVector> fv = store.getFeatures("SBIN", "5m", 14);
        assertTrue(fv.isPresent());
        assertTrue(fv.get().rsi() < 50, "RSI should be bearish (<50) in a downward trend: " + fv.get().rsi());
    }

    @Test
    void unknownSymbolReturnsEmpty() {
        assertFalse(store.getFeatures("UNKNOWN", "5m", 14).isPresent());
    }

    @Test
    void fileBasedStoreAutoRecoversAfterConnectionClosed() throws Exception {
        // Use a file-based store (not injected connection) to test connection recovery
        Path tempFile = Files.createTempFile("duckdb-feature-recovery-", ".duckdb");
        Files.deleteIfExists(tempFile);
        try (DuckDbFeatureStore fileStore = new DuckDbFeatureStore(tempFile)) {
            // Feed several candles before closing (need >=5 for feature computation)
            long baseTs = 1_710_000_000_000L;
            for (int i = 0; i < 6; i++) {
                long startMs = baseTs + (i * 300_000L);
                long endMs = startMs + 300_000L;
                long open = 75_000L + (i * 100L);
                long close = open + 200L;
                fileStore.feed(new CandleClosed(EventMetadata.root(),
                        new Candle("SBIN", "5m", startMs, endMs,
                                open, close + 50L, open - 50L, close, 10_000L, true)));
            }

            // Close the underlying JDBC connection
            fileStore.close();

            // Feed another candle — should trigger ensureConnection() → reconnect
            fileStore.feed(new CandleClosed(EventMetadata.root(),
                    new Candle("SBIN", "5m", baseTs + (6 * 300_000L), baseTs + (6 * 300_000L) + 300_000L,
                            75_600L, 75_800L, 75_100L, 75_600L, 15_000L, true)));

            // Verify features are still accessible through recovered connection
            Optional<FeatureVector> fv = fileStore.getFeatures("SBIN", "5m", 5);
            assertTrue(fv.isPresent(), "Features should be accessible after auto-reconnect");
            assertTrue(fv.get().rsi() > 0);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ── Helpers ──

    private void feedCandle(String symbol, String interval, long startMs, long endMs,
                            long open, long high, long low, long close, long volume) {
        store.feed(new CandleClosed(EventMetadata.root(),
                new Candle(symbol, interval, startMs, endMs, open, high, low, close, volume, true)));
    }

    private void feedCandleDev(String symbol, String interval, long startMs, long endMs,
                               long open, long high, long low, long close, long volume) {
        store.feed(new CandleDeveloping(EventMetadata.root(),
                new Candle(symbol, interval, startMs, endMs, open, high, low, close, volume, false)));
    }
}
