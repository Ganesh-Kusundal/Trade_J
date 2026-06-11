package com.tradej.historical.ingest.sync;

import com.tradej.core.domain.model.Candle;
import com.tradej.historical.ingest.canonical.ParquetWriteService;
import com.tradej.persistence.duckdb.DuckDbConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports candles from the runtime DuckDB event store to canonical parquet.
 *
 * <p>Bridges the gap between live trading data (runtime DuckDB) and the
 * analytics engine (parquet). Run after market close so that intraday
 * candles collected during the trading session become visible to the
 * DuckDbAnalyticsEngine for next-day queries.
 */
public final class RuntimeParquetExporter {

    private static final Logger log = LoggerFactory.getLogger(RuntimeParquetExporter.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final Path runtimeDbPath;
    private final ParquetWriteService parquetWriter;
    private final String segment;

    public RuntimeParquetExporter(Path runtimeDbPath, ParquetWriteService parquetWriter, String segment) {
        this.runtimeDbPath = runtimeDbPath;
        this.parquetWriter = parquetWriter;
        this.segment = segment;
    }

    public ExportResult exportDate(LocalDate date) {
        long fromMs = date.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = date.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;

        if (!runtimeDbPath.toFile().exists()) {
            log.warn("Runtime DB not found at {} — skipping export", runtimeDbPath);
            return new ExportResult(date, 0, 0, ExportStatus.SKIPPED_NO_DB);
        }

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + runtimeDbPath.toAbsolutePath())) {
            Map<String, List<Candle>> bySymbol = readCandlesBySymbol(conn, fromMs, toMs);

            if (bySymbol.isEmpty()) {
                log.info("No candles in runtime DB for {}", date);
                return new ExportResult(date, 0, 0, ExportStatus.NO_DATA);
            }

            int totalBars = 0;
            int symbolsExported = 0;
            for (var entry : bySymbol.entrySet()) {
                String symbol = entry.getKey();
                List<Candle> candles = entry.getValue();
                int written = parquetWriter.writeBars(segment, symbol, "1m", candles);
                totalBars += written;
                symbolsExported++;
            }

            log.info("Exported {} bars for {} symbols from runtime DB to parquet for {}",
                    totalBars, symbolsExported, date);
            return new ExportResult(date, symbolsExported, totalBars, ExportStatus.SUCCESS);

        } catch (SQLException ex) {
            log.error("Failed to export runtime candles for {}: {}", date, ex.getMessage());
            return new ExportResult(date, 0, 0, ExportStatus.FAILED);
        }
    }

    private Map<String, List<Candle>> readCandlesBySymbol(Connection conn, long fromMs, long toMs)
            throws SQLException {
        Map<String, List<Candle>> bySymbol = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT symbol, interval, start_time_ms, end_time_ms,
                       open_paisa, high_paisa, low_paisa, close_paisa, volume
                FROM candles
                WHERE start_time_ms >= ? AND start_time_ms < ?
                ORDER BY symbol, start_time_ms
                """)) {
            ps.setLong(1, fromMs);
            ps.setLong(2, toMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String symbol = rs.getString("symbol");
                    Candle candle = new Candle(
                            symbol,
                            rs.getString("interval"),
                            rs.getLong("start_time_ms"),
                            rs.getLong("end_time_ms"),
                            rs.getLong("open_paisa"),
                            rs.getLong("high_paisa"),
                            rs.getLong("low_paisa"),
                            rs.getLong("close_paisa"),
                            rs.getLong("volume"),
                            true
                    );
                    bySymbol.computeIfAbsent(symbol, k -> new ArrayList<>()).add(candle);
                }
            }
        }
        return bySymbol;
    }

    public record ExportResult(
            LocalDate date,
            int symbolsExported,
            int barsExported,
            ExportStatus status
    ) {}

    public enum ExportStatus {
        SUCCESS,
        NO_DATA,
        SKIPPED_NO_DB,
        FAILED
    }
}
