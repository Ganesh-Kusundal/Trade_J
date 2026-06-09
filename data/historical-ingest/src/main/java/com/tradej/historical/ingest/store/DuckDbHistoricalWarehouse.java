package com.tradej.historical.ingest.store;

import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.historical.ingest.model.DownloadJobRecord;
import com.tradej.historical.ingest.model.DownloadJobStats;
import com.tradej.historical.ingest.model.DownloadJobStatus;
import com.tradej.historical.ingest.model.DownloadSourceType;
import com.tradej.historical.ingest.model.DownloadTaskRecord;
import com.tradej.historical.ingest.model.DownloadTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DuckDbHistoricalWarehouse implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DuckDbHistoricalWarehouse.class);

    private final com.tradej.persistence.duckdb.DuckDbConnectionPool pool;
    private final boolean ownsPool;
    private final Path databasePath;
    private Connection rawConnection;

    public DuckDbHistoricalWarehouse(Path databasePath) {
        this.databasePath = databasePath;
        try {
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create directories for " + databasePath, e);
        }
        this.pool = com.tradej.persistence.duckdb.DuckDbConnectionPool.create(databasePath);
        this.ownsPool = true;
        this.rawConnection = pool.rawConnection();
        try {
            bootstrap(rawConnection);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to bootstrap historical warehouse at " + databasePath, e);
        }
    }

    public DuckDbHistoricalWarehouse(com.tradej.persistence.duckdb.DuckDbConnectionPool pool) {
        this.pool = pool;
        this.ownsPool = false;
        this.databasePath = null;
        this.rawConnection = pool.rawConnection();
        try {
            bootstrap(rawConnection);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to bootstrap historical warehouse", e);
        }
    }

    private Connection connection() {
        return pool != null ? pool.rawConnection() : rawConnection;
    }

    public synchronized void bootstrap(Connection conn) throws SQLException {
        if (hasLegacySchema(conn)) {
            throw new IllegalStateException(
                    "Legacy warehouse schema detected — run `download reset` before reusing this warehouse");
        }
        createCanonicalTables(conn);
    }

    private boolean hasLegacySchema(Connection conn) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "rolling_option_bars", "expiry_flag")) {
            return rs.next();
        }
    }

    private void dropLegacyTables(Connection conn) throws SQLException {
        conn.createStatement().execute("drop view if exists rolling_option_bars_15m");
        conn.createStatement().execute("drop table if exists download_tasks");
        conn.createStatement().execute("drop table if exists download_jobs");
        conn.createStatement().execute("drop table if exists rolling_option_bars");
    }

    private void createCanonicalTables(Connection conn) throws SQLException {
        conn.createStatement().execute("""
                create table if not exists rolling_option_bars (
                    underlying varchar not null,
                    expiry_kind varchar not null,
                    expiry_code integer not null,
                    strike_offset integer not null,
                    option_type varchar not null,
                    interval_min integer not null,
                    bar_time_ms bigint not null,
                    open_paisa bigint not null,
                    high_paisa bigint not null,
                    low_paisa bigint not null,
                    close_paisa bigint not null,
                    volume bigint not null,
                    iv double not null,
                    oi bigint not null,
                    spot_paisa bigint not null,
                    strike_paisa bigint not null,
                    ingested_at_ms bigint not null,
                    primary key (underlying, expiry_kind, expiry_code, strike_offset, option_type, interval_min, bar_time_ms)
                )
                """);
        conn.createStatement().execute("""
                create table if not exists download_jobs (
                    job_id varchar primary key,
                    source_type varchar not null,
                    status varchar not null,
                    config_json varchar not null,
                    created_at_ms bigint not null,
                    started_at_ms bigint,
                    finished_at_ms bigint,
                    stats_json varchar
                )
                """);
        conn.createStatement().execute("""
                create table if not exists download_tasks (
                    task_id varchar primary key,
                    job_id varchar not null,
                    fingerprint varchar not null,
                    underlying varchar not null,
                    expiry_kind varchar not null,
                    expiry_code integer not null,
                    strike_offset integer not null,
                    option_type varchar not null,
                    interval_min integer not null,
                    chunk_from date not null,
                    chunk_to date not null,
                    status varchar not null,
                    rows_written bigint not null default 0,
                    error varchar,
                    completed_at_ms bigint,
                    unique (job_id, fingerprint)
                )
                """);
        conn.createStatement().execute("""
                create or replace view rolling_option_bars_15m as
                select
                    underlying,
                    expiry_kind,
                    expiry_code,
                    strike_offset,
                    option_type,
                    15 as interval_min,
                    (bar_time_ms / 900000) * 900000 as bar_time_ms,
                    arg_min(open_paisa, bar_time_ms) as open_paisa,
                    max(high_paisa) as high_paisa,
                    min(low_paisa) as low_paisa,
                    arg_max(close_paisa, bar_time_ms) as close_paisa,
                    sum(volume) as volume,
                    arg_max(iv, bar_time_ms) as iv,
                    arg_max(oi, bar_time_ms) as oi,
                    arg_max(spot_paisa, bar_time_ms) as spot_paisa,
                    arg_max(strike_paisa, bar_time_ms) as strike_paisa,
                    max(ingested_at_ms) as ingested_at_ms
                from rolling_option_bars
                where interval_min = 5
                group by 1, 2, 3, 4, 5, 7
                """);
    }

    public synchronized void insertJob(DownloadJobRecord job) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                insert into download_jobs values (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, job.jobId());
            ps.setString(2, job.sourceType().name());
            ps.setString(3, job.status().name());
            ps.setString(4, job.configJson());
            ps.setLong(5, job.createdAtMs());
            if (job.startedAtMs() == null) {
                ps.setNull(6, java.sql.Types.BIGINT);
            } else {
                ps.setLong(6, job.startedAtMs());
            }
            if (job.finishedAtMs() == null) {
                ps.setNull(7, java.sql.Types.BIGINT);
            } else {
                ps.setLong(7, job.finishedAtMs());
            }
            ps.setString(8, job.statsJson());
            ps.executeUpdate();
        }
    }

    public synchronized void updateJobStatus(
            String jobId,
            DownloadJobStatus status,
            Long startedAtMs,
            Long finishedAtMs,
            String statsJson
    ) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                update download_jobs
                set status = ?, started_at_ms = coalesce(?, started_at_ms),
                    finished_at_ms = ?, stats_json = ?
                where job_id = ?
                """)) {
            ps.setString(1, status.name());
            if (startedAtMs == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, startedAtMs);
            }
            if (finishedAtMs == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, finishedAtMs);
            }
            ps.setString(4, statsJson);
            ps.setString(5, jobId);
            ps.executeUpdate();
        }
    }

    public synchronized Optional<DownloadJobRecord> findJob(String jobId) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                select job_id, source_type, status, config_json, created_at_ms,
                       started_at_ms, finished_at_ms, stats_json
                from download_jobs where job_id = ?
                """)) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapJob(rs));
            }
        }
    }

    public synchronized List<DownloadJobRecord> listRecentJobs(int limit) throws SQLException {
        int effectiveLimit = Math.max(1, Math.min(limit, 500));
        try (PreparedStatement ps = connection().prepareStatement("""
                select job_id, source_type, status, config_json, created_at_ms,
                       started_at_ms, finished_at_ms, stats_json
                from download_jobs
                order by created_at_ms desc
                limit ?
                """)) {
            ps.setInt(1, effectiveLimit);
            List<DownloadJobRecord> jobs = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    jobs.add(mapJob(rs));
                }
            }
            return jobs;
        }
    }

    public synchronized void insertTasks(List<DownloadTaskRecord> tasks) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                insert into download_tasks (
                    task_id, job_id, fingerprint, underlying, expiry_kind, expiry_code,
                    strike_offset, option_type, interval_min, chunk_from, chunk_to,
                    status, rows_written, error, completed_at_ms
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (job_id, fingerprint) do nothing
                """)) {
            for (DownloadTaskRecord task : tasks) {
                bindTask(ps, task);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public synchronized List<DownloadTaskRecord> listPendingTasks(String jobId) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                select task_id, job_id, fingerprint, underlying, expiry_kind, expiry_code,
                       strike_offset, option_type, interval_min, chunk_from, chunk_to,
                       status, rows_written, error, completed_at_ms
                from download_tasks
                where job_id = ? and status in ('PENDING', 'FAILED')
                order by underlying asc, chunk_from asc, expiry_kind asc, expiry_code asc,
                         strike_offset asc, option_type asc, interval_min asc
                """)) {
            ps.setString(1, jobId);
            return readTasks(ps);
        }
    }

    /**
     * Atomically claims the next pending/failed task for a job (single-JVM exclusive via synchronized).
     */
    public synchronized Optional<DownloadTaskRecord> claimNextTask(String jobId) throws SQLException {
        Optional<DownloadTaskRecord> next = findNextClaimableTask(jobId);
        if (next.isEmpty()) {
            return Optional.empty();
        }
        DownloadTaskRecord task = next.get();
        try (PreparedStatement ps = connection().prepareStatement("""
                update download_tasks
                set status = ?, rows_written = 0, error = null, completed_at_ms = null
                where task_id = ? and status in ('PENDING', 'FAILED')
                """)) {
            ps.setString(1, DownloadTaskStatus.RUNNING.name());
            ps.setString(2, task.taskId());
            if (ps.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return Optional.of(new DownloadTaskRecord(
                task.taskId(),
                task.jobId(),
                task.fingerprint(),
                task.underlying(),
                task.expiryKind(),
                task.expiryCode(),
                task.strikeOffset(),
                task.optionType(),
                task.intervalMin(),
                task.chunkFrom(),
                task.chunkTo(),
                DownloadTaskStatus.RUNNING,
                0L,
                null,
                null
        ));
    }

    /**
     * Resets orphaned RUNNING tasks back to PENDING (e.g. after crash mid-task).
     */
    public synchronized int resetStaleRunningTasks(String jobId) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                update download_tasks
                set status = 'PENDING', rows_written = 0, error = null, completed_at_ms = null
                where job_id = ? and status = 'RUNNING'
                """)) {
            ps.setString(1, jobId);
            return ps.executeUpdate();
        }
    }

    public synchronized void truncateDownloadData() throws SQLException {
        connection().createStatement().execute("truncate table rolling_option_bars");
        connection().createStatement().execute("truncate table download_tasks");
        connection().createStatement().execute("truncate table download_jobs");
    }

    private Optional<DownloadTaskRecord> findNextClaimableTask(String jobId) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                select task_id, job_id, fingerprint, underlying, expiry_kind, expiry_code,
                       strike_offset, option_type, interval_min, chunk_from, chunk_to,
                       status, rows_written, error, completed_at_ms
                from download_tasks
                where job_id = ? and status in ('PENDING', 'FAILED')
                order by underlying asc, chunk_from asc, expiry_kind asc, expiry_code asc,
                         strike_offset asc, option_type asc, interval_min asc
                limit 1
                """)) {
            ps.setString(1, jobId);
            List<DownloadTaskRecord> tasks = readTasks(ps);
            return tasks.isEmpty() ? Optional.empty() : Optional.of(tasks.getFirst());
        }
    }

    public synchronized void updateTask(
            String taskId,
            DownloadTaskStatus status,
            long rowsWritten,
            String error,
            Long completedAtMs
    ) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                update download_tasks
                set status = ?, rows_written = ?, error = ?, completed_at_ms = ?
                where task_id = ?
                """)) {
            ps.setString(1, status.name());
            ps.setLong(2, rowsWritten);
            ps.setString(3, error);
            if (completedAtMs == null) {
                ps.setNull(4, java.sql.Types.BIGINT);
            } else {
                ps.setLong(4, completedAtMs);
            }
            ps.setString(5, taskId);
            ps.executeUpdate();
        }
    }

    public synchronized DownloadJobStats jobStats(String jobId) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement("""
                select
                    count(*) as total,
                    count(*) filter (where status = 'PENDING') as pending,
                    count(*) filter (where status = 'RUNNING') as running,
                    count(*) filter (where status = 'COMPLETED') as completed,
                    count(*) filter (where status = 'FAILED') as failed,
                    coalesce(sum(rows_written), 0) as rows_written
                from download_tasks where job_id = ?
                """)) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new DownloadJobStats(
                        rs.getLong("total"),
                        rs.getLong("pending"),
                        rs.getLong("running"),
                        rs.getLong("completed"),
                        rs.getLong("failed"),
                        rs.getLong("rows_written")
                );
            }
        }
    }

    public synchronized long upsertRollingOptionBars(
            String underlying,
            String expiryKind,
            int expiryCode,
            int strikeOffset,
            String optionType,
            int intervalMin,
            List<RollingOptionBar> bars
    ) throws SQLException {
        if (bars.isEmpty()) {
            return 0L;
        }
        long ingestedAt = System.currentTimeMillis();
        long written = 0L;
        try (PreparedStatement ps = connection().prepareStatement("""
                insert into rolling_option_bars values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (underlying, expiry_kind, expiry_code, strike_offset, option_type, interval_min, bar_time_ms)
                do update set
                    open_paisa = excluded.open_paisa,
                    high_paisa = excluded.high_paisa,
                    low_paisa = excluded.low_paisa,
                    close_paisa = excluded.close_paisa,
                    volume = excluded.volume,
                    iv = excluded.iv,
                    oi = excluded.oi,
                    spot_paisa = excluded.spot_paisa,
                    strike_paisa = excluded.strike_paisa,
                    ingested_at_ms = excluded.ingested_at_ms
                """)) {
            for (RollingOptionBar bar : bars) {
                ps.setString(1, underlying);
                ps.setString(2, expiryKind);
                ps.setInt(3, expiryCode);
                ps.setInt(4, strikeOffset);
                ps.setString(5, optionType);
                ps.setInt(6, intervalMin);
                ps.setLong(7, bar.timestampMs());
                ps.setLong(8, bar.openPaisa());
                ps.setLong(9, bar.highPaisa());
                ps.setLong(10, bar.lowPaisa());
                ps.setLong(11, bar.closePaisa());
                ps.setLong(12, bar.volume());
                ps.setDouble(13, bar.iv());
                ps.setLong(14, bar.oi());
                ps.setLong(15, bar.spotPaisa());
                ps.setLong(16, bar.strikePaisa());
                ps.setLong(17, ingestedAt);
                ps.addBatch();
                written++;
            }
            ps.executeBatch();
        }
        return written;
    }

    public synchronized List<RollingOptionBar> queryRollingOptionBars(
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
        try (PreparedStatement ps = connection().prepareStatement("""
                select bar_time_ms, open_paisa, high_paisa, low_paisa, close_paisa,
                       volume, iv, oi, spot_paisa, strike_paisa
                from rolling_option_bars
                where underlying = ? and expiry_kind = ? and expiry_code = ?
                  and strike_offset = ? and option_type = ? and interval_min = ?
                  and bar_time_ms >= ? and bar_time_ms < ?
                order by bar_time_ms asc
                limit ?
                """)) {
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

    public synchronized long countRollingOptionBars() throws SQLException {
        try (ResultSet rs = connection().createStatement().executeQuery("select count(*) from rolling_option_bars")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void bindTask(PreparedStatement ps, DownloadTaskRecord task) throws SQLException {
        ps.setString(1, task.taskId());
        ps.setString(2, task.jobId());
        ps.setString(3, task.fingerprint());
        ps.setString(4, task.underlying());
        ps.setString(5, task.expiryKind());
        ps.setInt(6, task.expiryCode());
        ps.setInt(7, task.strikeOffset());
        ps.setString(8, task.optionType());
        ps.setInt(9, task.intervalMin());
        ps.setObject(10, task.chunkFrom());
        ps.setObject(11, task.chunkTo());
        ps.setString(12, task.status().name());
        ps.setLong(13, task.rowsWritten());
        ps.setString(14, task.error());
        if (task.completedAtMs() == null) {
            ps.setNull(15, java.sql.Types.BIGINT);
        } else {
            ps.setLong(15, task.completedAtMs());
        }
    }

    private static List<DownloadTaskRecord> readTasks(PreparedStatement ps) throws SQLException {
        List<DownloadTaskRecord> tasks = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tasks.add(new DownloadTaskRecord(
                        rs.getString("task_id"),
                        rs.getString("job_id"),
                        rs.getString("fingerprint"),
                        rs.getString("underlying"),
                        rs.getString("expiry_kind"),
                        rs.getInt("expiry_code"),
                        rs.getInt("strike_offset"),
                        rs.getString("option_type"),
                        rs.getInt("interval_min"),
                        rs.getDate("chunk_from").toLocalDate(),
                        rs.getDate("chunk_to").toLocalDate(),
                        DownloadTaskStatus.valueOf(rs.getString("status")),
                        rs.getLong("rows_written"),
                        rs.getString("error"),
                        rs.getObject("completed_at_ms") == null ? null : rs.getLong("completed_at_ms")
                ));
            }
        }
        return tasks;
    }

    private static DownloadJobRecord mapJob(ResultSet rs) throws SQLException {
        return new DownloadJobRecord(
                rs.getString("job_id"),
                DownloadSourceType.valueOf(rs.getString("source_type")),
                DownloadJobStatus.valueOf(rs.getString("status")),
                rs.getString("config_json"),
                rs.getLong("created_at_ms"),
                rs.getObject("started_at_ms") == null ? null : rs.getLong("started_at_ms"),
                rs.getObject("finished_at_ms") == null ? null : rs.getLong("finished_at_ms"),
                rs.getString("stats_json")
        );
    }

    public Path databasePath() {
        return databasePath;
    }

    @Override
    public synchronized void close() throws Exception {
        if (ownsPool && pool != null) {
            pool.close();
        } else if (pool == null && rawConnection != null) {
            rawConnection.close();
        }
    }
}
