package com.tradej.historical.ingest.canonical;

import com.tradej.core.domain.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CanonicalBarWriter implements ParquetWriteService {

    private static final Logger log = LoggerFactory.getLogger(CanonicalBarWriter.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final Path dataRoot;

    public CanonicalBarWriter(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    @Override
    public int writeBars(String segment, String symbol, String interval, List<Candle> candles) {
        if (candles.isEmpty()) {
            return 0;
        }

        Map<YearMonth, List<Candle>> byMonth = candles.stream()
                .collect(Collectors.groupingBy(c -> {
                    LocalDate d = Instant.ofEpochMilli(c.startTimeMs()).atZone(IST).toLocalDate();
                    return new YearMonth(d.getYear(), d.getMonthValue());
                }));

        int totalWritten = 0;
        long ingestedAt = System.currentTimeMillis();

        for (var entry : byMonth.entrySet()) {
            YearMonth ym = entry.getKey();
            List<Candle> monthCandles = entry.getValue();
            Path targetDir = CanonicalPaths.barsPath(dataRoot, segment, symbol, interval, ym.year, ym.month);
            try {
                Files.createDirectories(targetDir);
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot create directory " + targetDir, ex);
            }

            Path outputFile = targetDir.resolve("part.parquet").toAbsolutePath();
            writePartition(outputFile, segment, symbol, interval, monthCandles, ingestedAt);
            totalWritten += monthCandles.size();
        }

        log.info("Wrote {} bars for {} {} {} across {} months",
                totalWritten, symbol, segment, interval, byMonth.size());
        return totalWritten;
    }

    @Override
    public boolean hasData(String segment, String symbol, String interval, LocalDate date) {
        Path targetDir = CanonicalPaths.barsPath(dataRoot, segment, symbol, interval,
                date.getYear(), date.getMonthValue());
        Path outputFile = targetDir.resolve("part.parquet");
        return Files.exists(outputFile) && Files.isRegularFile(outputFile);
    }

    private void writePartition(Path outputFile, String segment, String symbol,
                                 String interval, List<Candle> candles, long ingestedAt) {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            conn.createStatement().execute("""
                    create temporary table bars (
                        bar_time_ms bigint,
                        open_paisa bigint,
                        high_paisa bigint,
                        low_paisa bigint,
                        close_paisa bigint,
                        volume bigint,
                        oi bigint,
                        trades bigint,
                        ingested_at_ms bigint
                    )
                    """);
            try (PreparedStatement ps = conn.prepareStatement(
                    "insert into bars values (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Candle c : candles) {
                    ps.setLong(1, c.startTimeMs());
                    ps.setLong(2, c.openPaisa());
                    ps.setLong(3, c.highPaisa());
                    ps.setLong(4, c.lowPaisa());
                    ps.setLong(5, c.closePaisa());
                    ps.setLong(6, c.volume());
                    ps.setLong(7, c.oi());
                    ps.setLong(8, c.trades());
                    ps.setLong(9, ingestedAt);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            Path tmpFile = outputFile.resolveSibling(outputFile.getFileName() + ".tmp");
            conn.createStatement().execute(
                    "copy bars to '" + escapePath(tmpFile) + "' (format parquet)");

            Files.move(tmpFile, outputFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to write parquet to " + outputFile, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to move tmp parquet to " + outputFile, ex);
        }
    }

    private static String escapePath(Path path) {
        return path.toString().replace("'", "''");
    }

    private record YearMonth(int year, int month) {}
}
