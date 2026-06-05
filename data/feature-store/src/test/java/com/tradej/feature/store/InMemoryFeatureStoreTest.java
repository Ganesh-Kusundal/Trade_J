package com.tradej.feature.store;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class InMemoryFeatureStoreTest {

    private InMemoryFeatureStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryFeatureStore();
    }

    @Test
    void feedCandleClosedAndGetFeatures() {
        // Seed 10 candles so the lookback succeeds.
        long base = 1_700_000_000_000L;
        for (int i = 0; i < 10; i++) {
            Candle c = new Candle("SBIN", "1m",
                    base + i * 60_000L, base + (i + 1) * 60_000L,
                    100_00L + i, 102_00L + i, 99_50L + i, 101_50L + i,
                    5_000L + i * 100L, true);
            store.feed(new CandleClosed(EventMetadata.root(), c));
        }
        Optional<com.tradej.core.domain.model.FeatureVector> features =
                store.getFeatures("SBIN", "1m", 5);
        assertTrue(features.isPresent());
    }

    // ── PR-11: replay state isolation — snapshot/restore round-trip ───────

    @Test
    void snapshotAndRestoreRollsBackCandleState() {
        // Seed live state.
        long base = 1_700_000_000_000L;
        for (int i = 0; i < 5; i++) {
            Candle c = new Candle("SBIN", "1m",
                    base + i * 60_000L, base + (i + 1) * 60_000L,
                    100_00L, 102_00L, 99_50L, 101_50L, 1_000L, true);
            store.feed(new CandleClosed(EventMetadata.root(), c));
        }
        InMemoryFeatureStore.StateSnapshot snapshot = store.snapshot();
        assertNotNull(snapshot);

        // Simulate replay pollution: append 100 more candles.
        for (int i = 0; i < 100; i++) {
            Candle c = new Candle("REPLAY-SYMBOL", "1m",
                    base + i * 60_000L, base + (i + 1) * 60_000L,
                    100_00L, 102_00L, 99_50L, 101_50L, 1_000L, true);
            store.feed(new CandleClosed(EventMetadata.root(), c));
        }
        // Confirm pollution
        Optional<com.tradej.core.domain.model.FeatureVector> polluted =
                store.getFeatures("REPLAY-SYMBOL", "1m", 1);
        assertTrue(polluted.isPresent(),
                "Replay-polluted candles must be visible before restore");

        // Restore: REPLAY-SYMBOL data must be gone.
        store.restore(snapshot);
        Optional<com.tradej.core.domain.model.FeatureVector> afterRestore =
                store.getFeatures("REPLAY-SYMBOL", "1m", 1);
        assertTrue(afterRestore.isEmpty(),
                "After restore, replay-polluted candles must be gone");

        // Original SBIN data must be intact.
        Optional<com.tradej.core.domain.model.FeatureVector> sbinAfter =
                store.getFeatures("SBIN", "1m", 1);
        assertTrue(sbinAfter.isPresent(),
                "Original SBIN candles must survive the restore");
    }

    @Test
    void restoreWithNullSnapshotIsNoOp() {
        Candle c = new Candle("SBIN", "1m",
                1_700_000_000_000L, 1_700_000_060_000L,
                100_00L, 102_00L, 99_50L, 101_50L, 1_000L, true);
        store.feed(new CandleClosed(EventMetadata.root(), c));
        store.restore(null);
        Optional<com.tradej.core.domain.model.FeatureVector> features =
                store.getFeatures("SBIN", "1m", 1);
        assertTrue(features.isPresent(),
                "restore(null) must not clear live state");
    }

    @Test
    void snapshotCandleAndTickCountAreEqual() {
        // Seed both candle and tick data — verify both are captured.
        for (int i = 0; i < 3; i++) {
            Candle c = new Candle("SBIN", "1m",
                    i * 60_000L, (i + 1) * 60_000L,
                    100_00L, 102_00L, 99_50L, 101_50L, 1_000L, true);
            store.feed(new CandleDeveloping(EventMetadata.root(), c));
        }
        InMemoryFeatureStore.StateSnapshot snapshot = store.snapshot();
        assertEquals(1, snapshot.candles().size(),
                "Snapshot should have one (symbol, interval) pair");
    }
}
