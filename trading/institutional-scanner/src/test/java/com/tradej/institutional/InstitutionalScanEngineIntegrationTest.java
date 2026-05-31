package com.tradej.institutional;

import com.tradej.historical.ingest.query.ParquetHistoricalBarRepository;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.institutional.model.InstitutionalScanConfig;
import com.tradej.institutional.model.InstitutionalScanResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class InstitutionalScanEngineIntegrationTest {

    @Test
    void runsFullPipelineOnLatestTradingDay() throws Exception {
        Path root = HistoricalEquityPaths.root(Path.of(HistoricalEquityPaths.DEFAULT_ROOT));
        assumeTrue(Files.exists(HistoricalEquityPaths.symbolsParquet(root)),
                "Parquet warehouse not present at " + root);

        try (ParquetHistoricalBarRepository repository = new ParquetHistoricalBarRepository(root)) {
            var scanDate = repository.latestAvailableTradingDay(30)
                    .orElseThrow(() -> new IllegalStateException("No trading day"));
            InstitutionalScanEngine engine = new InstitutionalScanEngine(repository, InstitutionalScanConfig.baseline());
            InstitutionalScanResult result = engine.runHistoricalScan(scanDate, "09:45:00");
            assertNotNull(result);
            assertFalse(result.candidates().isEmpty(), "Expected ranked candidates");
        }
    }
}
