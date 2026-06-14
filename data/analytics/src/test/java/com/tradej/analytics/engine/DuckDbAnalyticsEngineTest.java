package com.tradej.analytics.engine;

import com.tradej.analytics.config.DuckDbAnalyticsConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DuckDbAnalyticsEngineTest {

    @Test
    void bootstrap_emptyEquityRoot_doesNotThrow(@TempDir Path emptyEquityRoot) throws Exception {
        DuckDbAnalyticsConfig config = new DuckDbAnalyticsConfig(
                emptyEquityRoot,
                Path.of("does-not-exist-options.duckdb"),
                Path.of("does-not-exist-runtime.duckdb"),
                false,
                true,
                1_000,
                30_000L
        );

        DuckDbAnalyticsEngine engine = assertDoesNotThrow(
                () -> new DuckDbAnalyticsEngine(config),
                "Engine should construct cleanly against an empty equityRoot"
        );
        assertNotNull(engine, "Engine should be non-null after construction");

        try {
            long fromMs = 0L;
            long toMs = 1_700_000_000_000L;
            List<Map<String, Object>> candles = engine.queryEquityCandles("RELIANCE", fromMs, toMs, 100);
            assertNotNull(candles, "queryEquityCandles should return a list, not null");
            assertTrue(candles.isEmpty(), "Expected no candles for an empty equityRoot, got " + candles.size());
        } finally {
            engine.close();
        }
    }
}
