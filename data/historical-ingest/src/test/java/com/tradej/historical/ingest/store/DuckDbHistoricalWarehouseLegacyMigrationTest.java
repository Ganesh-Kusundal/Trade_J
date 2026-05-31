package com.tradej.historical.ingest.store;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("component")
class DuckDbHistoricalWarehouseLegacyMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsLegacySchemaOnBootstrap() throws Exception {
        Path dbPath = tempDir.resolve("legacy.duckdb");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath())) {
            connection.createStatement().execute("""
                    create table rolling_option_bars (
                        underlying varchar not null,
                        expiry_flag varchar not null,
                        expiry_code integer not null,
                        strike varchar not null,
                        option_type varchar not null,
                        interval_min integer not null,
                        bar_time_ms bigint not null,
                        open_paisa bigint not null,
                        high_paisa bigint not null,
                        low_paisa bigint not null,
                        close_paisa bigint not null,
                        volume bigint not null,
                        iv double not null,
                        oi bigint not null,
                        spot_paisa bigint not null,
                        strike_paisa bigint not null,
                        ingested_at_ms bigint not null,
                        primary key (underlying, expiry_flag, expiry_code, strike, option_type, interval_min, bar_time_ms)
                    )
                    """);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into rolling_option_bars values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, "NIFTY");
                ps.setString(2, "MONTH");
                ps.setInt(3, 1);
                ps.setString(4, "ATM");
                ps.setString(5, "CALL");
                ps.setInt(6, 5);
                ps.setLong(7, 1L);
                ps.setLong(8, 1L);
                ps.setLong(9, 1L);
                ps.setLong(10, 1L);
                ps.setLong(11, 1L);
                ps.setLong(12, 1L);
                ps.setDouble(13, 1.0);
                ps.setLong(14, 1L);
                ps.setLong(15, 1L);
                ps.setLong(16, 1L);
                ps.setLong(17, 1L);
                ps.executeUpdate();
            }
        }
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DuckDbHistoricalWarehouse(dbPath));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("download reset"),
                "Cause should instruct user to run download reset, got: " + ex.getCause().getMessage());
    }
}
