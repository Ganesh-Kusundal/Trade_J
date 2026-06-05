package com.tradej.persistence.duckdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.scan.ScanHit;
import com.tradej.core.domain.scan.ScanResult;
import com.tradej.core.domain.scan.ScanRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DuckDbScanStore implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DuckDbScanStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path databasePath;
    private Connection connection;

    public DuckDbScanStore(Path databasePath) {
        this.databasePath = databasePath;
        initConnection();
    }

    private void initConnection() {
        try {
            this.connection = DriverManager.getConnection("jdbc:duckdb:" + databasePath.toAbsolutePath());
            bootstrap();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize DuckDB scan store at " + databasePath, e);
        }
    }

    private synchronized void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            log.warn("DuckDB scan store connection lost — reconnecting");
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (Exception ignored) {
                // ignore
            }
            initConnection();
        }
    }

    private void bootstrap() throws SQLException {
        connection.createStatement().execute("""
                create table if not exists scan_runs (
                    run_id varchar primary key,
                    profile_id varchar not null,
                    started_at_ms bigint not null,
                    finished_at_ms bigint,
                    status varchar not null,
                    universe_size integer,
                    hit_count integer,
                    partial_failure_count integer,
                    error_message varchar
                )
                """);
        connection.createStatement().execute("""
                create table if not exists scan_hits (
                    run_id varchar not null,
                    symbol varchar not null,
                    exchange_segment varchar not null,
                    asset_class varchar not null,
                    underlying varchar,
                    score double not null,
                    reasons_json varchar,
                    snapshot_json varchar,
                    promoted boolean default false,
                    rank_order integer
                )
                """);
    }

    public synchronized void save(ScanResult result) throws SQLException {
        ensureConnection();
        ScanRun run = result.run();
        try (PreparedStatement ps = connection.prepareStatement("""
                insert into scan_runs (
                    run_id, profile_id, started_at_ms, finished_at_ms, status,
                    universe_size, hit_count, partial_failure_count, error_message
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, run.runId());
            ps.setString(2, run.profileId());
            ps.setLong(3, run.startedAtMs());
            ps.setLong(4, run.finishedAtMs());
            ps.setString(5, run.status().name());
            ps.setInt(6, run.universeSize());
            ps.setInt(7, run.hitCount());
            ps.setInt(8, run.partialFailureCount());
            ps.setString(9, run.errorMessage());
            ps.executeUpdate();
        }
        int rank = 0;
        for (ScanHit hit : result.hits()) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    insert into scan_hits (
                        run_id, symbol, exchange_segment, asset_class, underlying,
                        score, reasons_json, snapshot_json, promoted, rank_order
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, run.runId());
                ps.setString(2, hit.symbol());
                ps.setString(3, hit.exchangeSegment().name());
                ps.setString(4, hit.assetClass().name());
                ps.setString(5, hit.underlying());
                ps.setDouble(6, hit.score());
                ps.setString(7, toJson(hit.reasons()));
                ps.setString(8, toJson(hit.snapshotFields()));
                ps.setBoolean(9, hit.promoted());
                ps.setInt(10, rank++);
                ps.executeUpdate();
            }
        }
    }

    public synchronized Optional<ScanResult> latestByProfile(String profileId) throws SQLException {
        ensureConnection();
        try (PreparedStatement ps = connection.prepareStatement("""
                select run_id from scan_runs
                where profile_id = ?
                order by started_at_ms desc
                limit 1
                """)) {
            ps.setString(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return findByRunId(rs.getString("run_id"));
            }
        }
    }

    public synchronized Optional<ScanResult> findByRunId(String runId) throws SQLException {
        ensureConnection();
        ScanRun run;
        try (PreparedStatement ps = connection.prepareStatement("select * from scan_runs where run_id = ?")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                run = new ScanRun(
                        rs.getString("run_id"),
                        rs.getString("profile_id"),
                        rs.getLong("started_at_ms"),
                        rs.getLong("finished_at_ms"),
                        com.tradej.core.domain.scan.ScanRunStatus.valueOf(rs.getString("status")),
                        rs.getInt("universe_size"),
                        rs.getInt("hit_count"),
                        rs.getInt("partial_failure_count"),
                        rs.getString("error_message")
                );
            }
        }
        List<ScanHit> hits = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                select * from scan_hits where run_id = ? order by rank_order
                """)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hits.add(new ScanHit(
                            new com.tradej.core.domain.model.InstrumentKey(
                                    rs.getString("symbol"),
                                    com.tradej.core.domain.value.ExchangeSegment.valueOf(rs.getString("exchange_segment"))
                            ),
                            com.tradej.core.domain.scan.AssetClass.valueOf(rs.getString("asset_class")),
                            rs.getString("underlying"),
                            rs.getDouble("score"),
                            readStringList(rs.getString("reasons_json")),
                            readMap(rs.getString("snapshot_json")),
                            rs.getBoolean("promoted")
                    ));
                }
            }
        }
        return Optional.of(new ScanResult(run, hits));
    }

    public synchronized List<ScanRun> listRuns(String profileId, int limit) throws SQLException {
        ensureConnection();
        List<ScanRun> runs = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                select * from scan_runs
                where profile_id = ?
                order by started_at_ms desc
                limit ?
                """)) {
            ps.setString(1, profileId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    runs.add(new ScanRun(
                            rs.getString("run_id"),
                            rs.getString("profile_id"),
                            rs.getLong("started_at_ms"),
                            rs.getLong("finished_at_ms"),
                            com.tradej.core.domain.scan.ScanRunStatus.valueOf(rs.getString("status")),
                            rs.getInt("universe_size"),
                            rs.getInt("hit_count"),
                            rs.getInt("partial_failure_count"),
                            rs.getString("error_message")
                    ));
                }
            }
        }
        return List.copyOf(runs);
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warn("Failed to close DuckDB scan store: {}", e.getMessage());
            }
        }
    }

    private static String toJson(Object value) throws SQLException {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to parse reasons JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> readMap(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return java.util.Map.of();
        }
        try {
            return MAPPER.readValue(json, java.util.Map.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to parse snapshot JSON", e);
        }
    }
}
