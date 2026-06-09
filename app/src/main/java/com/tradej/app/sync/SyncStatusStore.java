package com.tradej.app.sync;

import com.tradej.historical.ingest.sync.DataGapScanService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory status tracker for sync operations.
 * Thread-safe via AtomicReference on the current status.
 */
@Component
public class SyncStatusStore {

    private final AtomicReference<SyncStatus> currentStatus = new AtomicReference<>(SyncStatus.idle());

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
        // no-op for now — could track per-date timing
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
    }

    public void failRun(String error) {
        update(s -> new SyncStatus("FAILED", s.trigger(), s.startedAt(), s.holidayRefreshAt(),
                Instant.now(), s.totalDates(), s.gapDates(), s.partialDates(), s.dateResults()));
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
