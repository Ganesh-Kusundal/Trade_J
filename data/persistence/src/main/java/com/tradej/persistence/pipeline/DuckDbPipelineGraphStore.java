package com.tradej.persistence.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.pipeline.graph.PipelineGraph;
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

/**
 * DuckDB-backed versioned store for declarative {@link PipelineGraph} definitions.
 */
public final class DuckDbPipelineGraphStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DuckDbPipelineGraphStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final com.tradej.persistence.duckdb.DuckDbConnectionPool pool;
    private final boolean ownsPool;
    private Connection rawConnection;

    public DuckDbPipelineGraphStore(Path databasePath) {
        this.pool = com.tradej.persistence.duckdb.DuckDbConnectionPool.create(databasePath);
        this.ownsPool = true;
        this.rawConnection = pool.rawConnection();
        bootstrap(rawConnection);
    }

    public DuckDbPipelineGraphStore(com.tradej.persistence.duckdb.DuckDbConnectionPool pool) {
        this.pool = pool;
        this.ownsPool = false;
        this.rawConnection = pool.rawConnection();
        bootstrap(rawConnection);
    }

    private Connection connection() {
        return pool != null ? pool.rawConnection() : rawConnection;
    }

    private void bootstrap(Connection conn) {
        try (var stmt = conn.createStatement()) {
            stmt.execute("""
                    create table if not exists pipeline_graphs (
                        graph_id varchar not null,
                        version integer not null,
                        name varchar not null,
                        graph_json varchar not null,
                        saved_at_ms bigint not null,
                        primary key (graph_id, version)
                    )
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to bootstrap pipeline graph store tables", e);
        }
    }

    public synchronized void save(PipelineGraph graph) throws SQLException {
        String json = toJson(graph);
        try (PreparedStatement ps = connection().prepareStatement("""
                insert into pipeline_graphs (graph_id, version, name, graph_json, saved_at_ms)
                values (?, ?, ?, ?, ?)
                on conflict (graph_id, version) do update set
                    name = excluded.name,
                    graph_json = excluded.graph_json,
                    saved_at_ms = excluded.saved_at_ms
                """)) {
            ps.setString(1, graph.id());
            ps.setInt(2, graph.version());
            ps.setString(3, graph.name());
            ps.setString(4, json);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
        log.info("Persisted pipeline graph id={} version={}", graph.id(), graph.version());
    }

    public synchronized Optional<PipelineGraph> loadLatest(String graphId) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                select graph_json from pipeline_graphs
                where graph_id = ?
                order by version desc
                limit 1
                """)) {
            ps.setString(1, graphId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(fromJson(rs.getString("graph_json")));
            }
        }
    }

    public synchronized Optional<PipelineGraph> loadVersion(String graphId, int version) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                select graph_json from pipeline_graphs
                where graph_id = ? and version = ?
                """)) {
            ps.setString(1, graphId);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(fromJson(rs.getString("graph_json")));
            }
        }
    }

    public synchronized List<PipelineGraphVersion> listVersions(String graphId) throws SQLException {
        List<PipelineGraphVersion> versions = new ArrayList<>();
        try (PreparedStatement ps = connection().prepareStatement("""
                select graph_id, version, name, saved_at_ms
                from pipeline_graphs
                where graph_id = ?
                order by version desc
                """)) {
            ps.setString(1, graphId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    versions.add(new PipelineGraphVersion(
                            rs.getString("graph_id"),
                            rs.getInt("version"),
                            rs.getString("name"),
                            rs.getLong("saved_at_ms")
                    ));
                }
            }
        }
        return List.copyOf(versions);
    }

    @Override
    public synchronized void close() {
        if (ownsPool && pool != null) {
            pool.close();
        } else if (pool == null && rawConnection != null) {
            try {
                rawConnection.close();
            } catch (SQLException e) {
                log.warn("Failed to close pipeline graph store: {}", e.getMessage());
            }
        }
    }

    private static String toJson(PipelineGraph graph) throws SQLException {
        try {
            return MAPPER.writeValueAsString(graph);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize pipeline graph", e);
        }
    }

    private static PipelineGraph fromJson(String json) throws SQLException {
        try {
            return MAPPER.readValue(json, PipelineGraph.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to deserialize pipeline graph", e);
        }
    }

    public record PipelineGraphVersion(String graphId, int version, String name, long savedAtMs) {
    }
}
