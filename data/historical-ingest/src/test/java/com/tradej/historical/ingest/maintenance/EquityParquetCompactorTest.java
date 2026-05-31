package com.tradej.historical.ingest.maintenance;

import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class EquityParquetCompactorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void compactsTaskParquetIntoHiveMonthPartition(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("historical-equity");
        Path symbolDir = HistoricalEquityPaths.symbolBarDir(root, "interval=1m", "TEST");
        symbolDir.toFile().mkdirs();

        long barTime = LocalDate.of(2024, 3, 15).atTime(10, 0).atZone(IST).toInstant().toEpochMilli();
        writeTaskParquet(symbolDir.resolve("part-task-1.parquet"), "TEST", barTime);

        EquityParquetCompactor.CompactionResult result = new EquityParquetCompactor(root).compact();
        assertEquals(1, result.symbolsProcessed());
        assertEquals(1, result.taskFilesMerged());
        assertEquals(1, result.partitionsWritten());
        assertFalse(symbolDir.resolve("part-task-1.parquet").toFile().exists());
        assertTrue(symbolDir.resolve("part-hive-2024-03.parquet").toFile().exists());

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            try (ResultSet rs = conn.createStatement().executeQuery("""
                    select count(*) as cnt from read_parquet('%s')
                    """.formatted(symbolDir.resolve("part-hive-2024-03.parquet").toAbsolutePath()))) {
                rs.next();
                assertEquals(1, rs.getLong("cnt"));
            }
        }
    }

    private static void writeTaskParquet(Path output, String symbol, long barTimeMs) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            conn.createStatement().execute("""
                    create temporary table equity_bars (
                        symbol varchar, interval varchar, bar_time_ms bigint,
                        open_paisa bigint, high_paisa bigint, low_paisa bigint, close_paisa bigint,
                        volume bigint, ingested_at_ms bigint
                    )
                    """);
            conn.createStatement().execute("""
                    insert into equity_bars values
                    ('%s', '1minute', %d, 100, 110, 90, 105, 1000, %d)
                    """.formatted(symbol, barTimeMs, Instant.now().toEpochMilli()));
            conn.createStatement().execute(
                    "copy equity_bars to '" + output.toAbsolutePath() + "' (format parquet)");
        }
    }
}
