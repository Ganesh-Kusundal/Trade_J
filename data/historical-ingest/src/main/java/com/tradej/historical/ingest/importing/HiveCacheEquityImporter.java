package com.tradej.historical.ingest.importing;

import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.historical.ingest.universe.LocalUniverseImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class HiveCacheEquityImporter {

    private static final Logger log = LoggerFactory.getLogger(HiveCacheEquityImporter.class);
    private static final int PROGRESS_EVERY = 100;

    private final LocalUniverseImporter universeImporter = new LocalUniverseImporter();

    public HiveCacheImportResult importHive(HiveCacheImportConfig config) throws SQLException, IOException {
        long startedAt = System.currentTimeMillis();
        Path sourceHive = config.sourceHive().toAbsolutePath().normalize();
        Path targetRoot = HistoricalEquityPaths.root(config.targetRoot()).toAbsolutePath().normalize();

        if (!Files.isDirectory(sourceHive)) {
            throw new IllegalArgumentException("Source hive directory not found: " + sourceHive);
        }

        List<String> months = discoverPartitions(sourceHive, config.fromMonth(), config.toMonth());
        if (months.isEmpty()) {
            throw new IllegalStateException(
                    "No year_month partitions found in " + sourceHive + " for range "
                            + config.fromMonth() + " .. " + resolveToMonth(sourceHive, config.toMonth())
            );
        }

        if (config.importUniverse()) {
            universeImporter.importUniverse(targetRoot, config.universeCsv(), config.industryParquet());
        }

        List<String> symbols = config.symbolFilter().isEmpty()
                ? universeImporter.loadSymbols(config.universeCsv())
                : config.symbolFilter();

        Files.createDirectories(HistoricalEquityPaths.barsDir(targetRoot, "interval=1m"));

        int filesWritten = 0;
        int filesSkipped = 0;
        int filesMissingSource = 0;
        int filesFailed = 0;
        long totalRowsWritten = 0;
        List<String> failedDetails = new ArrayList<>();
        Map<String, Integer> missingBySymbol = new HashMap<>();

        long ingestedAtMs = System.currentTimeMillis();
        int totalTasks = symbols.size() * months.size();
        int completed = 0;

        log.info(
                "Starting hive import: {} symbols x {} months = {} tasks into {}",
                symbols.size(), months.size(), totalTasks, targetRoot
        );

        for (String symbol : symbols) {
            int missingForSymbol = 0;
            for (String month : months) {
                completed++;
                Path outputFile = outputFile(targetRoot, symbol, month);
                Path sourceFile = sourceFile(sourceHive, month, symbol);

                if (!config.force() && isNonEmptyFile(outputFile)) {
                    filesSkipped++;
                    logProgress(completed, totalTasks, symbol, month, "skipped");
                    continue;
                }

                if (!Files.isRegularFile(sourceFile)) {
                    filesMissingSource++;
                    missingForSymbol++;
                    logProgress(completed, totalTasks, symbol, month, "missing-source");
                    continue;
                }

                try {
                    Files.createDirectories(outputFile.getParent());
                    long rows = transformMonth(sourceFile, outputFile, symbol, ingestedAtMs);
                    filesWritten++;
                    totalRowsWritten += rows;
                    logProgress(completed, totalTasks, symbol, month, "wrote " + rows + " rows");
                } catch (Exception ex) {
                    filesFailed++;
                    String detail = symbol + "/" + month + ": " + ex.getMessage();
                    failedDetails.add(detail);
                    log.warn("Failed to import {} {}: {}", symbol, month, ex.getMessage());
                    if (Files.exists(outputFile)) {
                        try {
                            Files.delete(outputFile);
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
            if (missingForSymbol > 0) {
                missingBySymbol.put(symbol, missingForSymbol);
            }
        }

        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info(
                "Hive import finished in {} ms: written={}, skipped={}, missing={}, failed={}, rows={}",
                elapsedMs, filesWritten, filesSkipped, filesMissingSource, filesFailed, totalRowsWritten
        );

        return new HiveCacheImportResult(
                months.size(),
                symbols.size(),
                filesWritten,
                filesSkipped,
                filesMissingSource,
                filesFailed,
                totalRowsWritten,
                elapsedMs,
                failedDetails,
                topMissingSymbols(missingBySymbol, 20)
        );
    }

    public List<String> discoverPartitions(Path sourceHive, String fromMonth, String toMonth) throws IOException {
        String resolvedTo = resolveToMonth(sourceHive, toMonth);
        try (Stream<Path> stream = Files.list(sourceHive)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("year_month="))
                    .map(name -> name.substring("year_month=".length()))
                    .filter(month -> month.compareTo(fromMonth) >= 0 && month.compareTo(resolvedTo) <= 0)
                    .sorted()
                    .toList();
        }
    }

    public String resolveToMonth(Path sourceHive, String toMonth) throws IOException {
        if (toMonth != null && !toMonth.isBlank()) {
            return toMonth.trim();
        }
        try (Stream<Path> stream = Files.list(sourceHive)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("year_month="))
                    .map(name -> name.substring("year_month=".length()))
                    .max(Comparator.naturalOrder())
                    .orElseThrow(() -> new IllegalStateException("No year_month partitions under " + sourceHive));
        }
    }

    private long transformMonth(Path sourceFile, Path outputFile, String symbol, long ingestedAtMs)
            throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            String sql = """
                    copy (
                        select
                            upper('%s') as symbol,
                            '1m' as interval,
                            epoch_ms(date) as bar_time_ms,
                            cast(round(open * 100) as bigint) as open_paisa,
                            cast(round(high * 100) as bigint) as high_paisa,
                            cast(round(low * 100) as bigint) as low_paisa,
                            cast(round(close * 100) as bigint) as close_paisa,
                            cast(volume as bigint) as volume,
                            %d as ingested_at_ms
                        from read_parquet('%s')
                        order by bar_time_ms asc
                    ) to '%s' (format parquet)
                    """.formatted(
                    escapeSqlLiteral(symbol),
                    ingestedAtMs,
                    escapePath(sourceFile),
                    escapePath(outputFile)
            );
            conn.createStatement().execute(sql);

            try (ResultSet rs = conn.createStatement().executeQuery(
                    "select count(*) from read_parquet('" + escapePath(outputFile) + "')"
            )) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static Path outputFile(Path targetRoot, String symbol, String month) {
        return HistoricalEquityPaths.symbolBarDir(targetRoot, "interval=1m", symbol)
                .resolve("part-hive-" + month + ".parquet");
    }

    private static Path sourceFile(Path sourceHive, String month, String symbol) {
        return sourceHive.resolve("year_month=" + month).resolve(symbol + ".parquet");
    }

    private static boolean isNonEmptyFile(Path path) throws IOException {
        return Files.isRegularFile(path) && Files.size(path) > 0;
    }

    private static void logProgress(int completed, int total, String symbol, String month, String status) {
        if (completed % PROGRESS_EVERY != 0 && completed != total) {
            return;
        }
        log.info("Import progress {}/{} {} {} {}", completed, total, symbol, month, status);
    }

    private static Map<String, Integer> topMissingSymbols(Map<String, Integer> missingBySymbol, int limit) {
        return missingBySymbol.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private static String escapePath(Path path) {
        return path.toAbsolutePath().toString().replace("'", "''");
    }

    private static String escapeSqlLiteral(String value) {
        return value.replace("'", "''").toUpperCase(Locale.ROOT);
    }
}
