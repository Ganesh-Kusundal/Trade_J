package com.tradej.historical.ingest.sync;

import com.tradej.persistence.duckdb.DuckDbConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares candle counts between the runtime event store (trade.duckdb)
 * and the feature store (trade-features.duckdb) to detect data drift.
 */
public final class CrossStoreReconciliation {

    private static final Logger log = LoggerFactory.getLogger(CrossStoreReconciliation.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final Path eventStorePath;
    private final Path featureStorePath;

    public CrossStoreReconciliation(Path eventStorePath, Path featureStorePath) {
        this.eventStorePath = eventStorePath;
        this.featureStorePath = featureStorePath;
    }

    public ReconciliationReport check(LocalDate date) {
        long fromMs = date.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = date.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;

        if (!eventStorePath.toFile().exists() || !featureStorePath.toFile().exists()) {
            return new ReconciliationReport(date, 0, 0, 0, ReconciliationStatus.SKIPPED);
        }

        try (Connection eventConn = DriverManager.getConnection("jdbc:duckdb:" + eventStorePath.toAbsolutePath());
             Connection featureConn = DriverManager.getConnection("jdbc:duckdb:" + featureStorePath.toAbsolutePath())) {

            long eventCandles = countCandles(eventConn, "candles", fromMs, toMs);
            long featureCandles = countCandles(featureConn, "feature_candles", fromMs, toMs);
            long drift = Math.abs(eventCandles - featureCandles);

            ReconciliationStatus status;
            if (eventCandles == 0 && featureCandles == 0) {
                status = ReconciliationStatus.NO_DATA;
            } else if (drift == 0) {
                status = ReconciliationStatus.CONSISTENT;
            } else if (drift <= Math.max(eventCandles, featureCandles) * 0.05) {
                status = ReconciliationStatus.MINOR_DRIFT;
            } else {
                status = ReconciliationStatus.SIGNIFICANT_DRIFT;
            }

            if (status != ReconciliationStatus.CONSISTENT && status != ReconciliationStatus.NO_DATA) {
                log.warn("Cross-store drift on {}: event_store={} feature_store={} drift={} status={}",
                        date, eventCandles, featureCandles, drift, status);
            }

            return new ReconciliationReport(date, eventCandles, featureCandles, drift, status);

        } catch (SQLException ex) {
            log.error("Cross-store reconciliation failed for {}: {}", date, ex.getMessage());
            return new ReconciliationReport(date, 0, 0, 0, ReconciliationStatus.FAILED);
        }
    }

    public List<ReconciliationReport> checkRange(LocalDate from, LocalDate to) {
        List<ReconciliationReport> reports = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            reports.add(check(cursor));
            cursor = cursor.plusDays(1);
        }
        return reports;
    }

    private long countCandles(Connection conn, String table, long fromMs, long toMs) throws SQLException {
        String timeCol = table.equals("feature_candles") ? "start_time_ms" : "start_time_ms";
        try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT count(*) FROM " + table +
                " WHERE " + timeCol + " >= " + fromMs + " AND " + timeCol + " < " + toMs)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException ex) {
            if (ex.getMessage().contains("does not exist") || ex.getMessage().contains("Table")) {
                return 0;
            }
            throw ex;
        }
    }

    public record ReconciliationReport(
            LocalDate date,
            long eventStoreCandles,
            long featureStoreCandles,
            long drift,
            ReconciliationStatus status
    ) {}

    public enum ReconciliationStatus {
        CONSISTENT,
        MINOR_DRIFT,
        SIGNIFICANT_DRIFT,
        NO_DATA,
        SKIPPED,
        FAILED
    }
}
