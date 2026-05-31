package com.tradej.analytics.engine;

import com.tradej.analytics.config.DuckDbAnalyticsConfig;
import com.tradej.analytics.guard.AnalyticsSqlGuard;
import com.tradej.core.domain.model.AnalyticsCatalogSnapshot;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.historical.ingest.universe.HivePartitionResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DuckDbAnalyticsEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DuckDbAnalyticsEngine.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String INTERVAL_FOLDER = "interval=1m";

    public static final String VIEW_EQUITY_BARS_1M = "equity_bars_1m";
    public static final String VIEW_EQUITY_UNIVERSE = "equity_universe";
    public static final String VIEW_ROLLING_OPTION_BARS = "rolling_option_bars";

    private final DuckDbAnalyticsConfig config;
    private final Path equityRoot;
    private Connection connection;
    private boolean optionsAttached;
    private boolean runtimeAttached;

    public DuckDbAnalyticsEngine(DuckDbAnalyticsConfig config) {
        this.config = config;
        this.equityRoot = HistoricalEquityPaths.root(config.equityRoot());
    }

    public DuckDbAnalyticsConfig config() {
        return config;
    }

    public Path equityRoot() {
        return equityRoot;
    }

    public boolean optionsAttached() {
        return optionsAttached;
    }

    public boolean runtimeAttached() {
        return runtimeAttached;
    }

    private void openConnection() {
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:");
            bootstrapViews();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to open analytics engine", ex);
        }
    }

    private synchronized void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            openConnection();
        }
    }

    private void bootstrapViews() throws SQLException {
        Path barsDir = HistoricalEquityPaths.barsDir(equityRoot, INTERVAL_FOLDER);
        if (!Files.isDirectory(barsDir)) {
            throw new IllegalStateException("Equity warehouse bars directory missing: " + barsDir);
        }
        Path barsGlob = barsDir.resolve("symbol=*").resolve("*.parquet").toAbsolutePath();
        connection.createStatement().execute("""
                create or replace view %s as
                select
                    symbol,
                    case when interval in ('1minute', '1m') then '1m' else interval end as interval,
                    bar_time_ms,
                    open_paisa, high_paisa, low_paisa, close_paisa, volume, ingested_at_ms
                from read_parquet('%s', hive_partitioning=true)
                """.formatted(VIEW_EQUITY_BARS_1M, escapeSqlPath(barsGlob)));

        Path symbolsPath = HistoricalEquityPaths.symbolsParquet(equityRoot).toAbsolutePath();
        Path industryPath = HistoricalEquityPaths.industryParquet(equityRoot).toAbsolutePath();
        if (Files.exists(symbolsPath) && Files.exists(industryPath)) {
            connection.createStatement().execute("""
                    create or replace view %s as
                    select
                        s.symbol,
                        s.company_name,
                        s.isin,
                        i.industry,
                        i.macro_sector,
                        s.as_of_date
                    from read_parquet('%s') s
                    left join read_parquet('%s') i using (symbol)
                    """.formatted(VIEW_EQUITY_UNIVERSE, escapeSqlPath(symbolsPath), escapeSqlPath(industryPath)));
        } else {
            connection.createStatement().execute("""
                    create or replace view %s as
                    select distinct
                        symbol,
                        symbol as company_name,
                        cast('' as varchar) as isin,
                        cast('' as varchar) as industry,
                        cast('' as varchar) as macro_sector,
                        cast(null as date) as as_of_date
                    from %s
                    """.formatted(VIEW_EQUITY_UNIVERSE, VIEW_EQUITY_BARS_1M));
        }

        optionsAttached = attachOptionsWarehouse();
        runtimeAttached = attachRuntimeWarehouse();
    }

    private boolean attachOptionsWarehouse() throws SQLException {
        Path warehouse = config.optionsWarehousePath().toAbsolutePath();
        if (!Files.isRegularFile(warehouse)) {
            connection.createStatement().execute("""
                    create or replace view %s as
                    select
                        cast(null as varchar) as underlying,
                        cast(null as varchar) as expiry_kind,
                        cast(null as integer) as expiry_code,
                        cast(null as integer) as strike_offset,
                        cast(null as varchar) as option_type,
                        cast(null as integer) as interval_min,
                        cast(null as bigint) as bar_time_ms,
                        cast(null as bigint) as open_paisa,
                        cast(null as bigint) as high_paisa,
                        cast(null as bigint) as low_paisa,
                        cast(null as bigint) as close_paisa,
                        cast(null as bigint) as volume,
                        cast(null as double) as iv,
                        cast(null as bigint) as oi,
                        cast(null as bigint) as spot_paisa,
                        cast(null as bigint) as strike_paisa,
                        cast(null as bigint) as ingested_at_ms
                    where 1 = 0
                    """.formatted(VIEW_ROLLING_OPTION_BARS));
            log.warn("Options warehouse not found at {} — rolling_option_bars view is empty", warehouse);
            return false;
        }
        connection.createStatement().execute(
                "attach '" + escapeSqlPath(warehouse) + "' as options_wh (read_only)");
        connection.createStatement().execute("""
                create or replace view %s as
                select * from options_wh.main.rolling_option_bars
                """.formatted(VIEW_ROLLING_OPTION_BARS));
        return true;
    }

    private boolean attachRuntimeWarehouse() throws SQLException {
        if (!config.attachRuntimeDb()) {
            return false;
        }
        Path runtimeDb = config.runtimeDbPath().toAbsolutePath();
        if (!Files.isRegularFile(runtimeDb)) {
            log.warn("Runtime DB not found at {} — skipping attach", runtimeDb);
            return false;
        }
        connection.createStatement().execute(
                "attach '" + escapeSqlPath(runtimeDb) + "' as runtime_wh (read_only)");
        return true;
    }

    public List<Map<String, Object>> queryEquityCandles(
            String symbol,
            long fromMs,
            long toMs,
            int limit
    ) throws SQLException {
        return queryEquityCandlesForSymbols(List.of(symbol), fromMs, toMs, limit);
    }

    public List<Map<String, Object>> queryEquityCandlesForSymbols(
            List<String> symbols,
            long fromMs,
            long toMs,
            int limit
    ) throws SQLException {
        ensureConnection();
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        LocalDate fromDate = Instant.ofEpochMilli(fromMs).atZone(IST).toLocalDate();
        LocalDate toDate = Instant.ofEpochMilli(toMs).atZone(IST).toLocalDate();
        List<String> partitions = HivePartitionResolver.partitionsForRange(fromDate, toDate);
        if (!partitions.isEmpty()) {
            String parquetSource = buildPartitionParquetSource(partitions);
            if (parquetSource != null) {
                return executeEquitySymbolRangeQuery(symbols, fromMs, toMs, limit, parquetSource);
            }
        }
        return executeEquitySymbolRangeQuery(symbols, fromMs, toMs, limit, VIEW_EQUITY_BARS_1M);
    }

    public List<Map<String, Object>> queryEquityUniverse() throws SQLException {
        ensureConnection();
        try (ResultSet rs = connection.createStatement().executeQuery("""
                select symbol, company_name, isin, industry, macro_sector, as_of_date
                from %s
                order by symbol asc
                """.formatted(VIEW_EQUITY_UNIVERSE))) {
            return readUniverseRows(rs);
        }
    }

    public Optional<LocalDate> latestEquityTradingDay(int lookbackDays) throws SQLException {
        ensureConnection();
        Optional<String> latestPartition = HistoricalEquityPaths.latestHivePartitionFile(equityRoot, INTERVAL_FOLDER);
        if (latestPartition.isEmpty()) {
            return Optional.empty();
        }
        Path latestMonthGlob = HistoricalEquityPaths.partitionGlob(equityRoot, INTERVAL_FOLDER, latestPartition.get());
        try (ResultSet rs = connection.createStatement().executeQuery("""
                select max(bar_time_ms) as latest_ms
                from read_parquet('%s', hive_partitioning=true)
                """.formatted(escapeSqlPath(latestMonthGlob.toAbsolutePath())))) {
            if (rs.next()) {
                long latestMs = rs.getLong("latest_ms");
                if (!rs.wasNull() && latestMs > 0) {
                    LocalDate latestDate = Instant.ofEpochMilli(latestMs).atZone(IST).toLocalDate();
                    if (lookbackDays > 0) {
                        LocalDate earliestAllowed = LocalDate.now(IST).minusDays(lookbackDays);
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

    public List<String> queryEquitySymbolsWithDataOn(LocalDate date, int limit) throws SQLException {
        ensureConnection();
        if (date == null || limit <= 0) {
            return List.of();
        }
        long fromMs = date.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = date.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli() - 1;
        String partitionFile = HivePartitionResolver.partitionFileForDate(date);
        Path dayGlob = HistoricalEquityPaths.partitionGlob(equityRoot, INTERVAL_FOLDER, partitionFile);
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

    public List<RollingOptionBar> queryRollingOptionBars(
            String underlying,
            String expiryKind,
            int expiryCode,
            int strikeOffset,
            String optionType,
            int intervalMin,
            long fromMs,
            long toMs,
            int limit
    ) throws SQLException {
        ensureConnection();
        if (!optionsAttached) {
            throw new IllegalStateException("Options warehouse is not attached");
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                select bar_time_ms, open_paisa, high_paisa, low_paisa, close_paisa,
                       volume, iv, oi, spot_paisa, strike_paisa
                from %s
                where underlying = ? and expiry_kind = ? and expiry_code = ?
                  and strike_offset = ? and option_type = ? and interval_min = ?
                  and bar_time_ms >= ? and bar_time_ms < ?
                order by bar_time_ms asc
                limit ?
                """.formatted(VIEW_ROLLING_OPTION_BARS))) {
            ps.setString(1, underlying);
            ps.setString(2, expiryKind);
            ps.setInt(3, expiryCode);
            ps.setInt(4, strikeOffset);
            ps.setString(5, optionType);
            ps.setInt(6, intervalMin);
            ps.setLong(7, fromMs);
            ps.setLong(8, toMs);
            ps.setInt(9, limit);
            List<RollingOptionBar> bars = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bars.add(new RollingOptionBar(
                            rs.getLong("bar_time_ms"),
                            rs.getLong("open_paisa"),
                            rs.getLong("high_paisa"),
                            rs.getLong("low_paisa"),
                            rs.getLong("close_paisa"),
                            rs.getLong("volume"),
                            rs.getDouble("iv"),
                            rs.getLong("oi"),
                            rs.getLong("spot_paisa"),
                            rs.getLong("strike_paisa")
                    ));
                }
            }
            return bars;
        }
    }

    public List<String> availableOptionUnderlyings() throws SQLException {
        ensureConnection();
        if (!optionsAttached) {
            return List.of();
        }
        try (ResultSet rs = connection.createStatement().executeQuery("""
                select distinct underlying from %s order by underlying asc
                """.formatted(VIEW_ROLLING_OPTION_BARS))) {
            List<String> underlyings = new ArrayList<>();
            while (rs.next()) {
                underlyings.add(rs.getString("underlying"));
            }
            return underlyings;
        }
    }

    public Optional<LocalDate> latestOptionTradingDay(String underlying, int lookbackDays) throws SQLException {
        ensureConnection();
        if (!optionsAttached) {
            return Optional.empty();
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                select max(bar_time_ms) as latest_ms
                from %s
                where underlying = ?
                """.formatted(VIEW_ROLLING_OPTION_BARS))) {
            ps.setString(1, underlying);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long latestMs = rs.getLong("latest_ms");
                    if (!rs.wasNull() && latestMs > 0) {
                        LocalDate latestDate = Instant.ofEpochMilli(latestMs).atZone(IST).toLocalDate();
                        if (lookbackDays > 0) {
                            LocalDate earliestAllowed = LocalDate.now(IST).minusDays(lookbackDays);
                            if (latestDate.isBefore(earliestAllowed)) {
                                return Optional.empty();
                            }
                        }
                        return Optional.of(latestDate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public AnalyticsCatalogSnapshot catalogSnapshot() throws SQLException {
        ensureConnection();
        long equitySymbols = scalarLong("""
                select count(distinct symbol) from %s
                """.formatted(VIEW_EQUITY_BARS_1M));
        long universeRows = scalarLong("select count(*) from " + VIEW_EQUITY_UNIVERSE);
        long optionsBars = optionsAttached
                ? scalarLong("select count(*) from " + VIEW_ROLLING_OPTION_BARS)
                : 0L;
        LocalDate equityMin = scalarDate("select min(bar_time_ms) from " + VIEW_EQUITY_BARS_1M);
        LocalDate equityMax = scalarDate("select max(bar_time_ms) from " + VIEW_EQUITY_BARS_1M);
        LocalDate optionsMin = optionsAttached
                ? scalarDate("select min(bar_time_ms) from " + VIEW_ROLLING_OPTION_BARS)
                : null;
        LocalDate optionsMax = optionsAttached
                ? scalarDate("select max(bar_time_ms) from " + VIEW_ROLLING_OPTION_BARS)
                : null;
        long barFileCount;
        try {
            barFileCount = countEquityParquetFiles();
        } catch (IOException ex) {
            throw new SQLException("Failed to count equity parquet files", ex);
        }
        Map<String, Object> views = Map.of(
                VIEW_EQUITY_BARS_1M, "Hive-partitioned Nifty 500 1m equity bars",
                VIEW_EQUITY_UNIVERSE, "Nifty 500 universe snapshot",
                VIEW_ROLLING_OPTION_BARS, "Dhan rolling option bars from attached warehouse"
        );
        return new AnalyticsCatalogSnapshot(
                equityRoot.toString(),
                config.optionsWarehousePath().toString(),
                equitySymbols,
                barFileCount,
                equityMin,
                equityMax,
                universeRows,
                optionsBars,
                optionsMin,
                optionsMax,
                optionsAttached,
                runtimeAttached,
                views
        );
    }

    public AnalyticsQueryResult executeReadOnlySql(String sql, int rowLimit) throws SQLException {
        if (!config.sqlEnabled()) {
            throw new IllegalStateException("Ad-hoc analytics SQL is disabled");
        }
        ensureConnection();
        String guarded = AnalyticsSqlGuard.applyRowLimit(sql, Math.min(rowLimit, config.sqlMaxRows()));
        long started = System.currentTimeMillis();
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout((int) Math.max(1, config.sqlMaxRuntimeMs() / 1000));
            try (ResultSet rs = statement.executeQuery(guarded)) {
                List<String> columns = new ArrayList<>();
                int columnCount = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(rs.getMetaData().getColumnLabel(i));
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(columns.get(i - 1), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return new AnalyticsQueryResult(columns, rows, rows.size(), System.currentTimeMillis() - started);
            }
        }
    }

    private long countEquityParquetFiles() throws IOException {
        Path barsDir = HistoricalEquityPaths.barsDir(equityRoot, INTERVAL_FOLDER);
        if (!Files.isDirectory(barsDir)) {
            return 0L;
        }
        try (var walk = Files.walk(barsDir)) {
            return walk.filter(path -> path.getFileName().toString().endsWith(".parquet")).count();
        }
    }

    private long scalarLong(String sql) throws SQLException {
        try (ResultSet rs = connection.createStatement().executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0L;
    }

    private LocalDate scalarDate(String sql) throws SQLException {
        try (ResultSet rs = connection.createStatement().executeQuery(sql)) {
            if (rs.next()) {
                long ms = rs.getLong(1);
                if (!rs.wasNull() && ms > 0) {
                    return Instant.ofEpochMilli(ms).atZone(IST).toLocalDate();
                }
            }
        }
        return null;
    }

    private String buildPartitionParquetSource(List<String> partitions) {
        List<String> paths = partitions.stream()
                .filter(partition -> partitionExists(partition))
                .map(partition -> HistoricalEquityPaths.partitionGlob(equityRoot, INTERVAL_FOLDER, partition)
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

    private boolean partitionExists(String partitionFile) {
        Path intervalDir = HistoricalEquityPaths.barsDir(equityRoot, INTERVAL_FOLDER);
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

    private List<Map<String, Object>> executeEquitySymbolRangeQuery(
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

    private static List<Map<String, Object>> readUniverseRows(ResultSet rs) throws SQLException {
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

    private static String escapeSqlPath(Path path) {
        return path.toString().replace("'", "''");
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
