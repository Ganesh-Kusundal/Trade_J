package com.tradej.historical.ingest.canonical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Migrates existing parquet files from the old layout to the canonical layout.
 *
 * <p>Old: {@code data/historical-equity/bars/interval=1m/symbol=SBIN/part-hive-YYYY-MM.parquet}
 * <p>New: {@code data/historical/bars/segment=NSE_EQ/symbol=SBIN/interval=1m/year=YYYY/month=MM/part.parquet}
 *
 * <p>Uses DuckDB to read old parquet and write to new partitioned layout.
 * Idempotent — safe to run multiple times.
 */
public final class ParquetMigrationService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ParquetMigrationService.class);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Connection connection;
    private final Path oldRoot;
    private final Path newRoot;

    public ParquetMigrationService(Path oldRoot, Path newRoot) {
        this.oldRoot = oldRoot;
        this.newRoot = newRoot;
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create DuckDB connection for migration", e);
        }
    }

    public MigrationResult migrate() {
        try {
            String oldGlob = oldRoot.resolve("bars/**/*.parquet").toString();
            Path newBarsRoot = CanonicalPaths.barsRoot(newRoot);
            Files.createDirectories(newBarsRoot);

            log.info("Migrating parquet from {} to {}", oldGlob, newBarsRoot);

            try (Statement stmt = connection.createStatement()) {
                // Count source rows
                ResultSet countRs = stmt.executeQuery(
                        "SELECT count(*) as total FROM read_parquet('" + oldGlob
                                + "', hive_partitioning=true)");
                countRs.next();
                long totalRows = countRs.getLong("total");
                log.info("Source: {} rows across {} symbols",
                        totalRows, countSymbols(oldGlob, stmt));

                // Copy to new partitioned layout
                String sql = "COPY ("
                        + "SELECT symbol, 'NSE_EQ' as segment, interval, "
                        + "  bar_time_ms, open_paisa, high_paisa, low_paisa, close_paisa, "
                        + "  volume, 0::BIGINT as oi, 0::BIGINT as trades, ingested_at_ms "
                        + "FROM read_parquet('" + oldGlob + "', hive_partitioning=true)"
                        + ") TO '" + newBarsRoot + "' ("
                        + "  FORMAT PARQUET,"
                        + "  PARTITION_BY (segment, symbol, interval),"
                        + "  OVERWRITE_OR_IGNORE"
                        + ")";
                stmt.execute(sql);

                // Verify
                String newGlob = newBarsRoot.resolve("**/*.parquet").toString();
                ResultSet verifyRs = stmt.executeQuery(
                        "SELECT count(*) as total FROM read_parquet('" + newGlob
                                + "', hive_partitioning=true)");
                verifyRs.next();
                long migratedRows = verifyRs.getLong("total");

                return new MigrationResult(totalRows, migratedRows,
                        totalRows == migratedRows ? MigrationStatus.SUCCESS : MigrationStatus.PARTIAL);
            }
        } catch (Exception e) {
            log.error("Migration failed: {}", e.getMessage());
            return new MigrationResult(0, 0, MigrationStatus.FAILED);
        }
    }

    private int countSymbols(String glob, Statement stmt) throws Exception {
        ResultSet rs = stmt.executeQuery(
                "SELECT count(DISTINCT symbol) as cnt FROM read_parquet('"
                        + glob + "', hive_partitioning=true)");
        rs.next();
        return rs.getInt("cnt");
    }

    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    public record MigrationResult(long sourceRows, long migratedRows, MigrationStatus status) {
        public boolean isSuccessful() {
            return status == MigrationStatus.SUCCESS;
        }
    }

    public enum MigrationStatus {
        SUCCESS,
        PARTIAL,
        FAILED
    }
}
