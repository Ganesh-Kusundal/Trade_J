package com.tradej.historical.ingest.canonical;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CanonicalBarQuery implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CanonicalBarQuery.class);

    private final Path barsRoot;
    private final Connection connection;

    public CanonicalBarQuery(Path barsRoot) {
        this.barsRoot = barsRoot;
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:");
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to open DuckDB connection for canonical bar queries", ex);
        }
    }

    public List<Map<String, Object>> queryBars(String symbol, String segment, String interval,
                                                 long fromMs, long toMs, int limit) throws SQLException {
        Path intervalDir = barsRoot
                .resolve(CanonicalPaths.SEGMENT_HIVE + "=" + segment)
                .resolve(CanonicalPaths.SYMBOL_HIVE + "=" + symbol)
                .resolve(CanonicalPaths.INTERVAL_HIVE + "=" + interval);

        if (!Files.isDirectory(intervalDir)) {
            return List.of();
        }

        String glob = intervalDir.toAbsolutePath().resolve("**").resolve("*.parquet")
                .toString().replace("'", "''");

        String sql = """
                select symbol, interval, bar_time_ms, open_paisa, high_paisa, low_paisa,
                       close_paisa, volume, oi, trades, ingested_at_ms
                from read_parquet('%s', hive_partitioning=true)
                where bar_time_ms >= ? and bar_time_ms <= ?
                order by bar_time_ms asc
                limit ?
                """.formatted(glob);

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, fromMs);
            ps.setLong(2, toMs);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                return readRows(rs);
            }
        }
    }

    public long countBars(String symbol, String segment, String interval) throws SQLException {
        Path intervalDir = barsRoot
                .resolve(CanonicalPaths.SEGMENT_HIVE + "=" + segment)
                .resolve(CanonicalPaths.SYMBOL_HIVE + "=" + symbol)
                .resolve(CanonicalPaths.INTERVAL_HIVE + "=" + interval);

        if (!Files.isDirectory(intervalDir)) {
            return 0;
        }

        String glob = intervalDir.toAbsolutePath().resolve("**").resolve("*.parquet")
                .toString().replace("'", "''");

        try (ResultSet rs = connection.createStatement().executeQuery("""
                select count(*) as cnt
                from read_parquet('%s', hive_partitioning=true)
                """.formatted(glob))) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public List<String> availableSymbols(String segment, String interval) throws SQLException {
        Path segmentDir = barsRoot.resolve(CanonicalPaths.SEGMENT_HIVE + "=" + segment);
        if (!Files.isDirectory(segmentDir)) {
            return List.of();
        }

        String glob = segmentDir.toAbsolutePath()
                .resolve("symbol=*")
                .resolve(CanonicalPaths.INTERVAL_HIVE + "=" + interval)
                .resolve("**").resolve("*.parquet")
                .toString().replace("'", "''");

        try (ResultSet rs = connection.createStatement().executeQuery("""
                select distinct symbol
                from read_parquet('%s', hive_partitioning=true)
                order by symbol asc
                """.formatted(glob))) {
            List<String> symbols = new ArrayList<>();
            while (rs.next()) {
                symbols.add(rs.getString("symbol"));
            }
            return symbols;
        }
    }

    public List<String> availableIntervals(String symbol, String segment) {
        Path symbolDir = barsRoot
                .resolve(CanonicalPaths.SEGMENT_HIVE + "=" + segment)
                .resolve(CanonicalPaths.SYMBOL_HIVE + "=" + symbol);
        if (!Files.isDirectory(symbolDir)) {
            return List.of();
        }
        try (var stream = Files.list(symbolDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(CanonicalPaths.INTERVAL_HIVE + "="))
                    .map(name -> name.substring(name.indexOf('=') + 1))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public Map<String, Object> summary(String segment) throws SQLException {
        Path segmentDir = barsRoot.resolve(CanonicalPaths.SEGMENT_HIVE + "=" + segment);
        if (!Files.isDirectory(segmentDir)) {
            return Map.of("symbols", 0, "totalBars", 0, "intervals", List.of());
        }

        String glob = segmentDir.toAbsolutePath()
                .resolve("**").resolve("*.parquet")
                .toString().replace("'", "''");

        try (ResultSet rs = connection.createStatement().executeQuery("""
                select count(*) as total, count(distinct symbol) as symbols,
                       count(distinct interval) as intervals,
                       min(bar_time_ms) as earliest, max(bar_time_ms) as latest
                from read_parquet('%s', hive_partitioning=true)
                """.formatted(glob))) {
            rs.next();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalBars", rs.getLong("total"));
            result.put("symbols", rs.getLong("symbols"));
            result.put("intervals", rs.getLong("intervals"));
            result.put("earliestMs", rs.getLong("earliest"));
            result.put("latestMs", rs.getLong("latest"));
            return result;
        }
    }

    private static List<Map<String, Object>> readRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("symbol", rs.getString("symbol"));
            row.put("interval", rs.getString("interval"));
            row.put("bar_time_ms", rs.getLong("bar_time_ms"));
            row.put("open_paisa", rs.getLong("open_paisa"));
            row.put("high_paisa", rs.getLong("high_paisa"));
            row.put("low_paisa", rs.getLong("low_paisa"));
            row.put("close_paisa", rs.getLong("close_paisa"));
            row.put("volume", rs.getLong("volume"));
            row.put("oi", rs.getLong("oi"));
            row.put("trades", rs.getLong("trades"));
            row.put("ingested_at_ms", rs.getLong("ingested_at_ms"));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
