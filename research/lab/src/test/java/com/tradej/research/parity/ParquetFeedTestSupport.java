package com.tradej.research.parity;

import com.tradej.analytics.config.DuckDbAnalyticsConfig;
import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.Candle;
import com.tradej.historical.ingest.canonical.CanonicalBarWriter;
import com.tradej.historical.ingest.canonical.CanonicalPaths;
import com.tradej.historical.ingest.universe.HivePartitionResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

public final class ParquetFeedTestSupport implements AutoCloseable {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String INTERVAL_FOLDER = "interval=1m";

    private final Path tempDir;
    private final Path dataRoot;
    private final Path equityRoot;
    private final CanonicalBarWriter writer;
    private final DuckDbAnalyticsEngine engine;

    public ParquetFeedTestSupport(Path tempDir) {
        this.tempDir = tempDir;
        this.dataRoot = tempDir.resolve("data");
        this.equityRoot = tempDir.resolve("equity");
        try {
            Files.createDirectories(dataRoot);
            Files.createDirectories(equityRoot.resolve("bars").resolve(INTERVAL_FOLDER));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create test directories", ex);
        }
        this.writer = new CanonicalBarWriter(dataRoot);
        DuckDbAnalyticsConfig config = new DuckDbAnalyticsConfig(
                equityRoot,
                equityRoot.resolve("options.duckdb"),
                equityRoot.resolve("runtime.duckdb"),
                false,
                false,
                DuckDbAnalyticsConfig.DEFAULT_SQL_MAX_ROWS,
                DuckDbAnalyticsConfig.DEFAULT_SQL_MAX_ROWS
        );
        this.engine = new DuckDbAnalyticsEngine(config);
    }

    public int writeCandles(String segment, String symbol, String interval, List<Candle> candles) {
        int written = writer.writeBars(segment, symbol, interval, candles);
        bridgeToEngineLayout(segment, symbol, interval, candles);
        return written;
    }

    public DuckDbAnalyticsEngine engine() {
        return engine;
    }

    public Path tempDir() {
        return tempDir;
    }

    public Path dataRoot() {
        return dataRoot;
    }

    public Path equityRoot() {
        return equityRoot;
    }

    private void bridgeToEngineLayout(String segment, String symbol, String interval, List<Candle> candles) {
        if (candles.isEmpty()) {
            return;
        }
        var byMonth = candles.stream()
                .collect(Collectors.groupingBy(c -> YearMonth.from(
                        Instant.ofEpochMilli(c.startTimeMs()).atZone(IST).toLocalDate())));
        for (var entry : byMonth.entrySet()) {
            YearMonth ym = entry.getKey();
            Path source = CanonicalPaths.barsPath(dataRoot, segment, symbol, interval,
                    ym.getYear(), ym.getMonthValue()).resolve("part.parquet");
            String partitionFile = HivePartitionResolver.partitionFileForMonth(ym);
            Path targetDir = equityRoot.resolve("bars").resolve(INTERVAL_FOLDER)
                    .resolve("symbol=" + symbol);
            Path target = targetDir.resolve(partitionFile);
            try {
                Files.createDirectories(targetDir);
                if (Files.exists(target)) {
                    Files.delete(target);
                }
                Files.createSymbolicLink(target, source);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to bridge parquet to engine layout: "
                        + target + " -> " + source, ex);
            }
        }
    }

    @Override
    public void close() {
        engine.close();
    }
}
