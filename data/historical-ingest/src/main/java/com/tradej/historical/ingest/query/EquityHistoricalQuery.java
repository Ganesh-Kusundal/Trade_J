package com.tradej.historical.ingest.query;

import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.historical.ingest.universe.HivePartitionResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class EquityHistoricalQuery implements AutoCloseable {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String INTERVAL_FOLDER = "interval=1m";

    private final Path rootPath;
    private Connection connection;

    public EquityHistoricalQuery(Path rootPath) {
        this.rootPath = HistoricalEquityPaths.root(rootPath);
        openConnection();
    }

    private void openConnection() {
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:");
            bootstrapViews();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to open equity historical query connection", ex);
        }
    }

    private void bootstrapViews() throws SQLException {
        Path barsGlob = HistoricalEquityPaths.barsDir(rootPath, "interval=1m")
                .resolve("symbol=*")
                .resolve("*.parquet")
                .toAbsolutePath();
        Path symbolsPath = HistoricalEquityPaths.symbolsParquet(rootPath).toAbsolutePath();
        Path industryPath = HistoricalEquityPaths.industryParquet(rootPath).toAbsolutePath();

        if (Files.exists(HistoricalEquityPaths.barsDir(rootPath, "interval=1m"))) {
            connection.createStatement().execute("""
                    create or replace view equity_bars_1m as
                    select
                        symbol,
                        case when interval in ('1minute', '1m') then '1m' else interval end as interval,
                        bar_time_ms,
                        open_paisa, high_paisa, low_paisa, close_paisa, volume, ingested_at_ms
                    from read_parquet('%s', hive_partitioning=true)
                    """.formatted(escapeSqlPath(barsGlob)));
        } else {
            connection.createStatement().execute("""
                    create or replace view equity_bars_1m as
                    select
                        cast(null as varchar) as symbol,
                        cast(null as varchar) as interval,
                        cast(null as bigint) as bar_time_ms,
                        cast(null as bigint) as open_paisa,
                        cast(null as bigint) as high_paisa,
                        cast(null as bigint) as low_paisa,
                        cast(null as bigint) as close_paisa,
                        cast(null as bigint) as volume,
                        cast(null as bigint) as ingested_at_ms
                    where 1 = 0
                    """);
        }

        if (Files.exists(symbolsPath) && Files.exists(industryPath)) {
            connection.createStatement().execute("""
                    create or replace view equity_universe as
                    select
                        s.symbol,
                        s.company_name,
                        s.isin,
                        i.industry,
                        i.macro_sector,
                        s.as_of_date
                    from read_parquet('%s') s
                    left join read_parquet('%s') i using (symbol)
                    """.formatted(escapeSqlPath(symbolsPath), escapeSqlPath(industryPath)));
        } else {
            connection.createStatement().execute("""
                    create or replace view equity_universe as
                    select
                        cast(null as varchar) as symbol,
                        cast(null as varchar) as company_name,
                        cast(null as varchar) as isin,
                        cast(null as varchar) as industry,
                        cast(null as varchar) as macro_sector,
                        cast(null as date) as as_of_date
                    where 1 = 0
                    """);
        }
    }

    public List<Map<String, Object>> queryCandles(
            String symbol,
            long fromMs,
            long toMs,
            int limit
    ) throws SQLException {
        return queryCandlesForSymbols(List.of(symbol), fromMs, toMs, limit);
    }

    public List<Map<String, Object>> queryCandlesForSymbols(
            List<String> symbols,
            long fromMs,
            long toMs,
            int limit
    ) throws SQLException {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        LocalDate fromDate = Instant.ofEpochMilli(fromMs).atZone(IST).toLocalDate();
        LocalDate toDate = Instant.ofEpochMilli(toMs).atZone(IST).toLocalDate();
        List<String> partitions = HivePartitionResolver.partitionsForRange(fromDate, toDate);
        if (!partitions.isEmpty()) {
            String parquetSource = buildPartitionParquetSource(partitions);
            if (parquetSource != null) {
                return executeSymbolRangeQuery(symbols, fromMs, toMs, limit, parquetSource);
            }
        }
        return executeSymbolRangeQuery(symbols, fromMs, toMs, limit, "equity_bars_1m");
    }

    private String buildPartitionParquetSource(List<String> partitions) {
        List<String> paths = partitions.stream()
                .filter(partition -> partitionExists(rootPath, partition))
                .map(partition -> HistoricalEquityPaths.partitionGlob(rootPath, INTERVAL_FOLDER, partition)
                        .toAbsolutePath()
                        .toString()
                        .replace("'", "''"))
                .toList();
        if (paths.isEmpty()) {
            return null;
        }
        if (paths.size() == 1) {
            return "read_parquet('" + paths.getFirst() + "', hive_partitioning=true)";
        }
        String array = paths.stream().map(path -> "'" + path + "'").collect(Collectors.joining(", "));
        return "read_parquet([" + array + "], hive_partitioning=true)";
    }

    private static boolean partitionExists(Path rootPath, String partitionFile) {
        Path intervalDir = HistoricalEquityPaths.barsDir(rootPath, INTERVAL_FOLDER);
        if (!Files.isDirectory(intervalDir)) {
            return false;
        }
        try (var symbolDirs = Files.list(intervalDir)) {
            return symbolDirs
                    .filter(Files::isDirectory)
                    .anyMatch(dir -> Files.isRegularFile(dir.resolve(partitionFile)));
        } catch (IOException ignored) {
            return false;
        }
    }

    private List<Map<String, Object>> executeSymbolRangeQuery(
            List<String> symbols,
            long fromMs,
            long toMs,
            int limit,
            String parquetSource
    ) throws SQLException {
        String placeholders = symbols.stream().map(symbol -> "?").collect(Collectors.joining(", "));
        String sql = """
                select symbol, interval, bar_time_ms, open_paisa, high_paisa, low_paisa, close_paisa, volume
                from %s
                where symbol in (%s)
                  and bar_time_ms >= ?
                  and bar_time_ms <= ?
                order by symbol asc, bar_time_ms asc
                limit ?
                """.formatted(parquetSource, placeholders);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (String symbol : symbols) {
                ps.setString(index++, symbol);
            }
            ps.setLong(index++, fromMs);
            ps.setLong(index++, toMs);
            ps.setInt(index, limit);
            try (ResultSet rs = ps.executeQuery()) {
                return readCandleRows(rs);
            }
        }
    }

    public long countCandles(String symbol) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                select count(*) from equity_bars_1m where symbol = ?
                """)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public Optional<LocalDate> latestAvailableTradingDay(int lookbackDays) throws SQLException {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        Optional<String> latestPartition = HistoricalEquityPaths.latestHivePartitionFile(rootPath, INTERVAL_FOLDER);
        if (latestPartition.isEmpty()) {
            return Optional.empty();
        }

        Path latestMonthGlob = HistoricalEquityPaths.partitionGlob(rootPath, INTERVAL_FOLDER, latestPartition.get());
        try (ResultSet rs = connection.createStatement().executeQuery("""
                select max(bar_time_ms) as latest_ms
                from read_parquet('%s', hive_partitioning=true)
                """.formatted(escapeSqlPath(latestMonthGlob.toAbsolutePath())))) {
            if (rs.next()) {
                long latestMs = rs.getLong("latest_ms");
                if (!rs.wasNull() && latestMs > 0) {
                    LocalDate latestDate = Instant.ofEpochMilli(latestMs).atZone(ist).toLocalDate();
                    if (lookbackDays > 0) {
                        LocalDate earliestAllowed = LocalDate.now(ist).minusDays(lookbackDays);
                        if (latestDate.isBefore(earliestAllowed)) {
                            return Optional.empty();
                        }
                    }
                    return Optional.of(latestDate);
                }
            }
        }
        return Optional.empty();
    }

    public List<String> querySymbolsWithDataOn(LocalDate date, int limit) throws SQLException {
        if (date == null || limit <= 0) {
            return List.of();
        }
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        long fromMs = date.atStartOfDay(ist).toInstant().toEpochMilli();
        long toMs = date.plusDays(1).atStartOfDay(ist).toInstant().toEpochMilli() - 1;

        String partitionFile = HivePartitionResolver.partitionFileForDate(date);
        Path dayGlob = HistoricalEquityPaths.partitionGlob(rootPath, INTERVAL_FOLDER, partitionFile);
        try (PreparedStatement ps = connection.prepareStatement("""
                select distinct symbol
                from read_parquet('%s', hive_partitioning=true)
                where bar_time_ms >= ?
                  and bar_time_ms <= ?
                order by symbol asc
                limit ?
                """.formatted(escapeSqlPath(dayGlob.toAbsolutePath())))) {
            ps.setLong(1, fromMs);
            ps.setLong(2, toMs);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> symbols = new ArrayList<>();
                while (rs.next()) {
                    symbols.add(rs.getString("symbol"));
                }
                return symbols;
            }
        }
    }

    public List<Map<String, Object>> queryUniverse() throws SQLException {
        try (ResultSet rs = connection.createStatement().executeQuery("""
                select symbol, company_name, isin, industry, macro_sector, as_of_date
                from equity_universe
                order by symbol asc
                """)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("symbol", rs.getString("symbol"));
                row.put("companyName", rs.getString("company_name"));
                row.put("isin", rs.getString("isin"));
                row.put("industry", rs.getString("industry"));
                row.put("macroSector", rs.getString("macro_sector"));
                row.put("asOfDate", rs.getObject("as_of_date"));
                rows.add(row);
            }
            return rows;
        }
    }

    private static List<Map<String, Object>> readCandleRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("symbol", rs.getString("symbol"));
            row.put("interval", rs.getString("interval"));
            row.put("barTimeMs", rs.getLong("bar_time_ms"));
            row.put("openPaisa", rs.getLong("open_paisa"));
            row.put("highPaisa", rs.getLong("high_paisa"));
            row.put("lowPaisa", rs.getLong("low_paisa"));
            row.put("closePaisa", rs.getLong("close_paisa"));
            row.put("volume", rs.getLong("volume"));
            rows.add(row);
        }
        return rows;
    }

    private static String escapeSqlPath(Path path) {
        return path.toString().replace("'", "''");
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
