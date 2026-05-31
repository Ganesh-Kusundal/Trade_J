package com.tradej.historical.ingest.maintenance;

import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.historical.ingest.universe.HivePartitionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Merges {@code part-{taskId}.parquet} download chunks into canonical
 * {@code part-hive-YYYY-MM.parquet} month partitions for hive pruning.
 */
public final class EquityParquetCompactor {

    private static final Logger log = LoggerFactory.getLogger(EquityParquetCompactor.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String INTERVAL_FOLDER = "interval=1m";

    private final Path rootPath;

    public EquityParquetCompactor(Path rootPath) {
        this.rootPath = HistoricalEquityPaths.root(rootPath);
    }

    public CompactionResult compact() throws SQLException, IOException {
        Path barsDir = HistoricalEquityPaths.barsDir(rootPath, INTERVAL_FOLDER);
        if (!Files.isDirectory(barsDir)) {
            return new CompactionResult(0, 0, 0, List.of());
        }
        int symbolsProcessed = 0;
        int filesMerged = 0;
        int partitionsWritten = 0;
        List<String> compactedSymbols = new ArrayList<>();
        try (Stream<Path> symbolDirs = Files.list(barsDir)) {
            List<Path> dirs = symbolDirs.filter(Files::isDirectory).sorted().toList();
            for (Path symbolDir : dirs) {
                String dirName = symbolDir.getFileName().toString();
                if (!dirName.startsWith("symbol=")) {
                    continue;
                }
                String symbol = dirName.substring("symbol=".length());
                List<Path> taskFiles = listTaskParquetFiles(symbolDir);
                if (taskFiles.isEmpty()) {
                    continue;
                }
                int written = compactSymbol(symbol, taskFiles);
                if (written > 0) {
                    symbolsProcessed++;
                    filesMerged += taskFiles.size();
                    partitionsWritten += written;
                    compactedSymbols.add(symbol);
                    for (Path taskFile : taskFiles) {
                        Files.deleteIfExists(taskFile);
                    }
                }
            }
        }
        log.info(
                "Equity parquet compaction complete: {} symbols, {} task files merged, {} hive partitions written",
                symbolsProcessed, filesMerged, partitionsWritten
        );
        return new CompactionResult(symbolsProcessed, filesMerged, partitionsWritten, compactedSymbols);
    }

    private int compactSymbol(String symbol, List<Path> taskFiles) throws SQLException {
        Set<YearMonth> months = new LinkedHashSet<>();
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            String unionSource = buildUnionSource(taskFiles);
            conn.createStatement().execute("""
                    create or replace temporary table task_bars as
                    select
                        symbol,
                        case when interval in ('1minute', '1m') then '1m' else interval end as interval,
                        bar_time_ms,
                        open_paisa, high_paisa, low_paisa, close_paisa, volume,
                        ingested_at_ms
                    from %s
                    where symbol = '%s'
                    """.formatted(unionSource, escapeSql(symbol)));

            try (ResultSet rs = conn.createStatement().executeQuery("""
                    select distinct bar_time_ms from task_bars order by bar_time_ms asc
                    """)) {
                while (rs.next()) {
                    LocalDate date = Instant.ofEpochMilli(rs.getLong("bar_time_ms")).atZone(IST).toLocalDate();
                    months.add(YearMonth.from(date));
                }
            }
            if (months.isEmpty()) {
                return 0;
            }

            int written = 0;
            Path symbolDir = HistoricalEquityPaths.symbolBarDir(rootPath, INTERVAL_FOLDER, symbol);
            for (YearMonth month : months) {
                String partitionFile = HivePartitionResolver.partitionFileForMonth(month);
                Path output = symbolDir.resolve(partitionFile).toAbsolutePath();
                Path existing = Files.exists(output) ? output : null;
                long monthStart = month.atDay(1).atStartOfDay(IST).toInstant().toEpochMilli();
                long monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(IST).toInstant().toEpochMilli();
                if (existing != null) {
                    conn.createStatement().execute("""
                            create or replace temporary table month_source as
                            select * from read_parquet('%s')
                            union all
                            select * from task_bars
                            where bar_time_ms >= %d and bar_time_ms < %d
                            """.formatted(escapePath(existing), monthStart, monthEnd));
                } else {
                    conn.createStatement().execute("""
                            create or replace temporary table month_source as
                            select * from task_bars
                            where bar_time_ms >= %d and bar_time_ms < %d
                            """.formatted(monthStart, monthEnd));
                }
                conn.createStatement().execute("""
                        copy (
                            select symbol, interval, bar_time_ms,
                                   open_paisa, high_paisa, low_paisa, close_paisa, volume, ingested_at_ms
                            from (
                                select *,
                                       row_number() over (partition by bar_time_ms order by ingested_at_ms desc) as rn
                                from month_source
                            ) ranked
                            where rn = 1
                            order by bar_time_ms asc
                        ) to '%s' (format parquet, overwrite_or_ignore true)
                        """.formatted(escapePath(output)));
                written++;
            }
            return written;
        }
    }

    private static List<Path> listTaskParquetFiles(Path symbolDir) throws IOException {
        try (Stream<Path> files = Files.list(symbolDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("part-")
                                && name.endsWith(".parquet")
                                && !name.startsWith("part-hive-");
                    })
                    .sorted()
                    .toList();
        }
    }

    private static String buildUnionSource(List<Path> taskFiles) {
        if (taskFiles.size() == 1) {
            return "read_parquet('" + escapePath(taskFiles.getFirst()) + "')";
        }
        String array = taskFiles.stream()
                .map(path -> "'" + escapePath(path) + "'")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return "read_parquet([" + array + "])";
    }

    private static String escapePath(Path path) {
        return path.toAbsolutePath().toString().replace("'", "''");
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }

    public record CompactionResult(
            int symbolsProcessed,
            int taskFilesMerged,
            int partitionsWritten,
            List<String> symbols
    ) {
    }
}
