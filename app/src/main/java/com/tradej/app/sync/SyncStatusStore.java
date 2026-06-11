package com.tradej.app.sync;

import com.tradej.historical.ingest.sync.DataGapScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SyncStatusStore {

    private static final Logger log = LoggerFactory.getLogger(SyncStatusStore.class);

    private final AtomicReference<SyncStatus> currentStatus = new AtomicReference<>(SyncStatus.idle());
    private final Path dbPath;

    public SyncStatusStore() {
        this.dbPath = null;
    }

    public SyncStatusStore(Path dbPath) {
        this.dbPath = dbPath;
        initTable();
    }

    private void initTable() {
        if (dbPath == null) return;
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath())) {
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS sync_runs (
                    run_id INTEGER,
                    trigger_type VARCHAR,
                    state VARCHAR,
                    started_at_ms BIGINT,
                    completed_at_ms BIGINT,
                    total_dates INTEGER,
                    gap_count INTEGER,
                    missing_dates VARCHAR,
                    summary VARCHAR
                )
                """);
        } catch (SQLException ex) {
            log.warn("Failed to init sync_runs table: {}", ex.getMessage());
        }
    }

    public SyncStatus getStatus() {
        return currentStatus.get();
    }

    public void startNewRun(String trigger) {
        currentStatus.set(new SyncStatus(
                "RUNNING", trigger, Instant.now(), null, null,
                0, List.of(), List.of(), List.of()
        ));
    }

    public void recordHolidayRefresh() {
        update(s -> new SyncStatus(s.state(), s.trigger(), s.startedAt(), Instant.now(),
                s.completedAt(), s.totalDates(), s.gapDates(), s.partialDates(), s.dateResults()));
    }

    public void recordGapScan(DataGapScanService.GapReport report) {
        update(s -> new SyncStatus(s.state(), s.trigger(), s.startedAt(), s.holidayRefreshAt(),
                s.completedAt(), report.totalTradingDays(),
                report.missingDates(), report.partialDates(), s.dateResults()));
    }

    public void recordSyncPlan(List<LocalDate> dates) {
        update(s -> new SyncStatus(s.state(), s.trigger(), s.startedAt(), s.holidayRefreshAt(),
                s.completedAt(), s.totalDates(), s.gapDates(), s.partialDates(), s.dateResults()));
    }

    public void recordDateSyncStart(LocalDate date) {
    }

    public void recordDateSyncComplete(LocalDate date, String summary) {
        update(s -> {
            var results = new ArrayList<>(s.dateResults());
            results.add(new DateResult(date, "COMPLETE", summary));
            return new SyncStatus(s.state(), s.trigger(), s.startedAt(), s.holidayRefreshAt(),
                    s.completedAt(), s.totalDates(), s.gapDates(), s.partialDates(), results);
        });
    }

    public void recordDateSyncFailed(LocalDate date, String error) {
        update(s -> {
            var results = new ArrayList<>(s.dateResults());
            results.add(new DateResult(date, "FAILED", error));
            return new SyncStatus(s.state(), s.trigger(), s.startedAt(), s.holidayRefreshAt(),
                    s.completedAt(), s.totalDates(), s.gapDates(), s.partialDates(), results);
        });
    }

    public void completeRun(String summary) {
        update(s -> new SyncStatus("COMPLETE", s.trigger(), s.startedAt(), s.holidayRefreshAt(),
                Instant.now(), s.totalDates(), s.gapDates(), s.partialDates(), s.dateResults()));
        persistRun("COMPLETE", summary);
    }

    public void failRun(String error) {
        update(s -> new SyncStatus("FAILED", s.trigger(), s.startedAt(), s.holidayRefreshAt(),
                Instant.now(), s.totalDates(), s.gapDates(), s.partialDates(), s.dateResults()));
        persistRun("FAILED", error);
    }

    private void persistRun(String state, String summary) {
        if (dbPath == null) return;
        SyncStatus s = currentStatus.get();
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath.toAbsolutePath())) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO sync_runs (trigger_type, state, started_at_ms, completed_at_ms,
                                           total_dates, gap_count, missing_dates, summary)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, s.trigger());
                ps.setString(2, state);
                ps.setLong(3, s.startedAt() != null ? s.startedAt().toEpochMilli() : 0);
                ps.setLong(4, s.completedAt() != null ? s.completedAt().toEpochMilli() : 0);
                ps.setInt(5, s.totalDates());
                ps.setInt(6, s.gapDates().size());
                ps.setString(7, s.gapDates().stream().map(LocalDate::toString)
                        .reduce((a, b) -> a + "," + b).orElse(""));
                ps.setString(8, summary);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            log.warn("Failed to persist sync run: {}", ex.getMessage());
        }
    }

    private void update(java.util.function.UnaryOperator<SyncStatus> fn) {
        currentStatus.updateAndGet(fn);
    }

    public record SyncStatus(
            String state,
            String trigger,
            Instant startedAt,
            Instant holidayRefreshAt,
            Instant completedAt,
            int totalDates,
            List<LocalDate> gapDates,
            List<LocalDate> partialDates,
            List<DateResult> dateResults
    ) {
        static SyncStatus idle() {
            return new SyncStatus("IDLE", null, null, null, null, 0, List.of(), List.of(), List.of());
        }
    }

    public record DateResult(LocalDate date, String status, String detail) {}
}
