package com.tradej.app.scanner;

import com.tradej.historical.ingest.query.ParquetHistoricalBarRepository;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.institutional.InstitutionalScanEngine;
import com.tradej.institutional.model.InstitutionalScanConfig;
import com.tradej.institutional.model.InstitutionalScanResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Validates the institutional scan path used by {@link ScanService#runInstitutionalScan}.
 */
@Tag("component")
class ScanServiceInstitutionalComponentTest {

    @Test
    void institutionalScanPathProducesHitsFromParquet() throws Exception {
        Path root = HistoricalEquityPaths.root(Path.of(HistoricalEquityPaths.DEFAULT_ROOT));
        assumeTrue(Files.exists(HistoricalEquityPaths.symbolsParquet(root)),
                "Parquet warehouse not present at " + root);

        try (ParquetHistoricalBarRepository repository = new ParquetHistoricalBarRepository(root)) {
            var scanDate = repository.latestAvailableTradingDay(30);
            assumeTrue(scanDate.isPresent(), "No parquet trading days under " + root);
            InstitutionalScanEngine engine = new InstitutionalScanEngine(repository, InstitutionalScanConfig.baseline());
            InstitutionalScanResult result = engine.runHistoricalScan(scanDate.get(), null);
            assertNotNull(result);
            assertFalse(result.candidates().isEmpty());
        }
    }
}
