package com.tradej.app.integration;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.query.ParquetHistoricalBarRepository;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.institutional.InstitutionalScanEngine;
import com.tradej.institutional.model.InstitutionalScanResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class InstitutionalScanIntegrationTest {

    @Test
    void readsParquetUniverseWhenPresent() throws Exception {
        Path root = Path.of(HistoricalEquityPaths.DEFAULT_ROOT);
        assumeTrue(Files.exists(root), "Parquet warehouse not present at " + root);

        try (ParquetHistoricalBarRepository repository = new ParquetHistoricalBarRepository(root)) {
            assertFalse(repository.queryUniverse().isEmpty());
            assertTrue(repository.latestAvailableTradingDay(30).isPresent());
        }
    }

    @Test
    void runsInstitutionalScanOnLatestTradingDay() throws Exception {
        Path root = Path.of(HistoricalEquityPaths.DEFAULT_ROOT);
        assumeTrue(Files.exists(root), "Parquet warehouse not present at " + root);

        try (ParquetHistoricalBarRepository repository = new ParquetHistoricalBarRepository(root)) {
            LocalDate scanDate = repository.latestAvailableTradingDay(30)
                    .orElseThrow(() -> new IllegalStateException("No trading day"));
            InstitutionalScanEngine engine = new InstitutionalScanEngine(repository);
            InstitutionalScanResult result = engine.runHistoricalScan(scanDate, "09:45:00");
            assertNotNull(result);
            assertFalse(result.candidates().isEmpty(), "Expected ranked candidates");
        }
    }

    @Test
    void queriesParquetCandlesForKnownSymbolWhenPresent() throws Exception {
        Path root = Path.of(HistoricalEquityPaths.DEFAULT_ROOT);
        assumeTrue(Files.exists(root), "Parquet warehouse not present at " + root);

        try (ParquetHistoricalBarRepository repository = new ParquetHistoricalBarRepository(root)) {
            LocalDate scanDate = repository.latestAvailableTradingDay(30).orElse(null);
            assumeTrue(scanDate != null, "No trading day available");
            var candles = repository.queryCandles(
                    InstrumentKey.of("SBIN", ExchangeSegment.NSE_EQ),
                    "5m",
                    scanDate,
                    scanDate
            );
            assertFalse(candles.isEmpty(), "Expected SBIN candles on " + scanDate);
        }
    }
}
