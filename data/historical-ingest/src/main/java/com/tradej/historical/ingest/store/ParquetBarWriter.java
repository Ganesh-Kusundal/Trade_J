package com.tradej.historical.ingest.store;

import com.tradej.core.domain.model.Candle;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class ParquetBarWriter {

    private static final Logger log = LoggerFactory.getLogger(ParquetBarWriter.class);

    private final Path rootPath;

    public ParquetBarWriter(Path rootPath) {
        this.rootPath = HistoricalEquityPaths.root(rootPath);
    }

    public Path write(String symbol, String intervalFolder, String interval, List<Candle> candles, String taskId)
            throws SQLException {
        if (candles.isEmpty()) {
            throw new IllegalArgumentException("Refusing to write empty candle batch for " + symbol);
        }
        Path outputDir = HistoricalEquityPaths.symbolBarDir(rootPath, intervalFolder, symbol);
        try {
            Files.createDirectories(outputDir);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Unable to create parquet directory " + outputDir, ex);
        }
        Path outputFile = outputDir.resolve("part-" + taskId + ".parquet").toAbsolutePath();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            conn.createStatement().execute("""
                    create temporary table equity_bars (
                        symbol varchar,
                        interval varchar,
                        bar_time_ms bigint,
                        open_paisa bigint,
                        high_paisa bigint,
                        low_paisa bigint,
                        close_paisa bigint,
                        volume bigint,
                        ingested_at_ms bigint
                    )
                    """);
            long ingestedAt = System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement("""
                    insert into equity_bars values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (Candle candle : candles) {
                    ps.setString(1, symbol);
                    ps.setString(2, interval);
                    ps.setLong(3, candle.startTimeMs());
                    ps.setLong(4, candle.openPaisa());
                    ps.setLong(5, candle.highPaisa());
                    ps.setLong(6, candle.lowPaisa());
                    ps.setLong(7, candle.closePaisa());
                    ps.setLong(8, candle.volume());
                    ps.setLong(9, ingestedAt);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.createStatement().execute(
                    "copy equity_bars to '" + escapePath(outputFile) + "' (format parquet)");
        }

        log.debug("Wrote {} bars for {} to {}", candles.size(), symbol, outputFile);
        return outputFile;
    }

    public boolean outputExists(String symbol, String intervalFolder, String taskId) {
        Path outputFile = HistoricalEquityPaths.symbolBarDir(rootPath, intervalFolder, symbol)
                .resolve("part-" + taskId + ".parquet");
        return Files.exists(outputFile);
    }

    private static String escapePath(Path path) {
        return path.toString().replace("'", "''");
    }
}
