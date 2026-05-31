package com.tradej.historical.ingest.planner;

import com.tradej.historical.ingest.model.DownloadTaskRecord;
import com.tradej.historical.ingest.model.DownloadTaskStatus;
import com.tradej.historical.ingest.model.EquityHistoricalDownloadConfig;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EquityHistoricalDownloadPlanner {

    public static final int INTRADAY_MAX_DAYS = 90;
    private static final String EQUITY_KIND = "EQUITY";

    public List<DownloadTaskRecord> planTasks(String jobId, EquityHistoricalDownloadConfig config) {
        List<DateWindow> windows = splitDateWindows(
                config.fromDate(),
                config.toDate(),
                INTRADAY_MAX_DAYS
        );
        List<DownloadTaskRecord> tasks = new ArrayList<>();
        for (String symbol : config.symbols()) {
            for (EquityHistoricalDownloadPlanner.DateWindow window : windows) {
                String fingerprint = symbol + "|"
                        + config.interval() + "|"
                        + config.exchangeSegment().name() + "|"
                        + window.fromDate() + "|"
                        + window.toDate();
                tasks.add(new DownloadTaskRecord(
                        UUID.randomUUID().toString(),
                        jobId,
                        fingerprint,
                        symbol,
                        EQUITY_KIND,
                        0,
                        0,
                        EQUITY_KIND,
                        config.intervalMinutes(),
                        window.fromDate(),
                        window.toDate(),
                        DownloadTaskStatus.PENDING,
                        0L,
                        null,
                        null
                ));
            }
        }
        return tasks;
    }

    public static long estimatedTaskCount(EquityHistoricalDownloadConfig config) {
        int windows = splitDateWindows(
                config.fromDate(),
                config.toDate(),
                INTRADAY_MAX_DAYS
        ).size();
        return (long) config.symbols().size() * windows;
    }

    static List<DateWindow> splitDateWindows(
            LocalDate fromDate,
            LocalDate toDate,
            int maxDaysPerRequest
    ) {
        List<DateWindow> windows = new ArrayList<>();
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            LocalDate windowEnd = cursor.plusDays(maxDaysPerRequest - 1L);
            if (windowEnd.isAfter(toDate)) {
                windowEnd = toDate;
            }
            windows.add(new DateWindow(cursor, windowEnd));
            cursor = windowEnd.plusDays(1L);
        }
        return windows;
    }

    record DateWindow(LocalDate fromDate, LocalDate toDate) {
    }
}
