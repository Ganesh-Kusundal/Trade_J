package com.tradej.historical.ingest.importing;

import com.tradej.historical.ingest.query.EquityHistoricalQuery;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class HiveCacheEquityImporterTest {

    @TempDir
    Path tempDir;

    @Test
    void transformsHivePartitionsIntoHistoricalEquityLayoutAndSkipsOnResume() throws Exception {
        Path sourceHive = tempDir.resolve("source-hive");
        Path targetRoot = tempDir.resolve("historical-equity");
        Path csvPath = tempDir.resolve("ind_nifty500list.csv");
        Path industryPath = tempDir.resolve("nse500_industry_mapping.parquet");

        Files.writeString(csvPath, """
                Company Name,Industry,Symbol,Series,ISIN Code
                Tata Consultancy Services Ltd.,Information Technology,TCS,EQ,INE467B01029
                Reliance Industries Ltd.,Oil Gas & Consumable Fuels,RELIANCE,EQ,INE002A01018
                """);

        writeIndustryParquet(industryPath);
        writeSourceMonth(sourceHive, "2020-01", "TCS", "2020-01-02 09:15:00", 2500.0, 1000.0);
        writeSourceMonth(sourceHive, "2026-03", "TCS", "2026-03-02 09:15:00", 2596.0, 137464.0);
        writeSourceMonth(sourceHive, "2026-03", "RELIANCE", "2026-03-02 09:15:00", 2800.0, 50000.0);

        HiveCacheEquityImporter importer = new HiveCacheEquityImporter();
        HiveCacheImportConfig config = new HiveCacheImportConfig(
                sourceHive,
                targetRoot,
                csvPath,
                industryPath,
                "2020-01",
                "2026-03",
                false,
                List.of(),
                true
        );

        HiveCacheImportResult first = importer.importHive(config);
        assertEquals(2, first.partitionsDiscovered());
        assertEquals(2, first.symbolsProcessed());
        assertEquals(3, first.filesWritten());
        assertTrue(first.totalRowsWritten() >= 3);

        assertTrue(Files.exists(HistoricalEquityPaths.symbolBarDir(targetRoot, "interval=1m", "TCS")
                .resolve("part-hive-2020-01.parquet")));
        assertTrue(Files.exists(HistoricalEquityPaths.symbolsParquet(targetRoot)));

        try (EquityHistoricalQuery query = new EquityHistoricalQuery(targetRoot)) {
            ZoneId ist = ZoneId.of("Asia/Kolkata");
            long fromMs = LocalDate.of(2020, 1, 1).atStartOfDay(ist).toInstant().toEpochMilli();
            long toMs = LocalDate.of(2026, 3, 31).atTime(23, 59).atZone(ist).toInstant().toEpochMilli();
            List<Map<String, Object>> tcsBars = query.queryCandles("TCS", fromMs, toMs, 10);
            assertTrue(tcsBars.size() >= 2);
            Map<String, Object> mar2026 = tcsBars.stream()
                    .filter(row -> ((Number) row.get("closePaisa")).longValue() == 259_600L)
                    .findFirst()
                    .orElseThrow();
            assertEquals(259_600L, mar2026.get("closePaisa"));
            assertEquals("1m", mar2026.get("interval"));
        }

        HiveCacheImportResult second = importer.importHive(config);
        assertEquals(0, second.filesWritten());
        assertEquals(3, second.filesSkipped());
    }

    @Test
    void discoversPartitionsWithinRequestedRange() throws Exception {
        Path sourceHive = tempDir.resolve("source-hive");
        Files.createDirectories(sourceHive.resolve("year_month=2019-12"));
        Files.createDirectories(sourceHive.resolve("year_month=2020-01"));
        Files.createDirectories(sourceHive.resolve("year_month=2026-05"));
        Files.createDirectories(sourceHive.resolve("year_month=2026-06"));

        HiveCacheEquityImporter importer = new HiveCacheEquityImporter();
        List<String> months = importer.discoverPartitions(sourceHive, "2020-01", "2026-05");
        assertEquals(List.of("2020-01", "2026-05"), months);
        assertEquals("2026-06", importer.resolveToMonth(sourceHive, null));
    }

    private static void writeIndustryParquet(Path industryPath) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            conn.createStatement().execute("""
                    copy (
                        select * from (values
                            ('TCS', 'Tata Consultancy Services Ltd.', 'Information Technology'),
                            ('RELIANCE', 'Reliance Industries Ltd.', 'Oil Gas & Consumable Fuels')
                        ) as t(symbol, company_name, industry)
                    ) to '%s' (format parquet)
                    """.formatted(industryPath.toAbsolutePath().toString().replace("'", "''")));
        }
    }

    private static void writeSourceMonth(
            Path sourceHive,
            String month,
            String symbol,
            String timestamp,
            double close,
            double volume
    ) throws Exception {
        Path partition = sourceHive.resolve("year_month=" + month);
        Files.createDirectories(partition);
        Path output = partition.resolve(symbol + ".parquet");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            conn.createStatement().execute("""
                    copy (
                        select
                            timestamp '%s' as date,
                            %f as open,
                            %f as high,
                            %f as low,
                            %f as close,
                            %f as volume,
                            '%s' as symbol,
                            'sync' as source,
                            '%s' as year_month
                    ) to '%s' (format parquet)
                    """.formatted(
                    timestamp,
                    close,
                    close + 1,
                    close - 1,
                    close,
                    volume,
                    symbol,
                    month,
                    output.toAbsolutePath().toString().replace("'", "''")
            ));
        }
    }
}
