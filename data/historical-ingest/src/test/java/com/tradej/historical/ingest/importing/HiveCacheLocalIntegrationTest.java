package com.tradej.historical.ingest.importing;

import com.tradej.historical.ingest.query.EquityHistoricalQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
@Tag("local-hive")
class HiveCacheLocalIntegrationTest {

    private static final Path DEFAULT_SOURCE_HIVE = Path.of(
            "/Users/apple/Downloads/playground2/broker/data_engine/cache/hive"
    );
    private static final Path DEFAULT_UNIVERSE_CSV = Path.of(
            "/Users/apple/Downloads/playground2/broker/data_engine/ind_nifty500list.csv"
    );
    private static final Path DEFAULT_INDUSTRY = Path.of(
            "/Users/apple/Downloads/playground2/broker/data_engine/nse500_industry_mapping.parquet"
    );

    @TempDir
    Path tempDir;

    @Test
    void importsTcsFromRealPlaygroundHiveSample() throws Exception {
        Path sourceHive = envPath("TRADE_HISTORICAL_EQUITY_SOURCE_HIVE", DEFAULT_SOURCE_HIVE);
        Path universeCsv = envPath("TRADE_HISTORICAL_EQUITY_SOURCE_UNIVERSE_CSV", DEFAULT_UNIVERSE_CSV);
        Path industryParquet = envPath("TRADE_HISTORICAL_EQUITY_SOURCE_INDUSTRY", DEFAULT_INDUSTRY);
        assumeTrue(Files.isDirectory(sourceHive), "playground2 hive cache not available");
        assumeTrue(Files.isRegularFile(universeCsv), "playground2 universe CSV not available");
        assumeTrue(Files.isRegularFile(industryParquet), "playground2 industry parquet not available");
        assumeTrue(
                Files.isRegularFile(sourceHive.resolve("year_month=2020-01/TCS.parquet")),
                "TCS 2020-01 source parquet not available"
        );
        assumeTrue(
                Files.isRegularFile(sourceHive.resolve("year_month=2026-03/TCS.parquet")),
                "TCS 2026-03 source parquet not available"
        );

        Path targetRoot = tempDir.resolve("historical-equity");
        HiveCacheImportConfig config = new HiveCacheImportConfig(
                sourceHive,
                targetRoot,
                universeCsv,
                industryParquet,
                "2020-01",
                "2026-03",
                true,
                List.of("TCS"),
                true
        );

        HiveCacheImportResult result = new HiveCacheEquityImporter().importHive(config);
        assertTrue(result.filesWritten() >= 2, "Expected TCS files for 2020-01 and 2026-03");
        assertTrue(result.totalRowsWritten() > 1000, "Expected substantial TCS history");

        try (EquityHistoricalQuery query = new EquityHistoricalQuery(targetRoot)) {
            long count = query.countCandles("TCS");
            assertTrue(count > 1000, "Expected TCS bars in imported warehouse, got " + count);
        }
    }

    private static Path envPath(String envVar, Path fallback) {
        String configured = System.getenv(envVar);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return fallback;
    }
}
