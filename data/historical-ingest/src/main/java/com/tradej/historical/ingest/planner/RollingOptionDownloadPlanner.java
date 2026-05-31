package com.tradej.historical.ingest.planner;

import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.historical.DhanHistoricalDataClient;
import com.tradej.core.domain.instrument.RollingOptionSeriesKey;
import com.tradej.historical.ingest.model.DownloadTaskRecord;
import com.tradej.historical.ingest.model.DownloadTaskStatus;
import com.tradej.historical.ingest.model.RollingOptionDownloadConfig;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RollingOptionDownloadPlanner {

    public List<DownloadTaskRecord> planTasks(String jobId, RollingOptionDownloadConfig config) {
        List<DhanHistoricalDataClient.DateWindow> windows = DhanHistoricalDataClient.splitDateWindows(
                config.fromDate(),
                config.toDate(),
                DhanProtocolConstants.ROLLING_OPTION_MAX_DAYS
        );
        List<DownloadTaskRecord> tasks = new ArrayList<>();
        for (String symbol : config.symbols()) {
            for (var expiry : config.expiries()) {
                for (var strikeOffset : config.strikes()) {
                    for (var optionType : config.optionTypes()) {
                        for (int intervalMin : config.intervalMinutes()) {
                            for (DhanHistoricalDataClient.DateWindow window : windows) {
                                RollingOptionSeriesKey seriesKey = new RollingOptionSeriesKey(
                                        symbol,
                                        config.exchangeSegment(),
                                        expiry,
                                        strikeOffset,
                                        optionType,
                                        intervalMin
                                );
                                String fingerprint = seriesKey.fingerprint(window.fromDate(), window.toDate());
                                tasks.add(new DownloadTaskRecord(
                                        UUID.randomUUID().toString(),
                                        jobId,
                                        fingerprint,
                                        seriesKey.underlying(),
                                        expiry.kind().name(),
                                        expiry.code(),
                                        strikeOffset.value(),
                                        optionType.name(),
                                        intervalMin,
                                        window.fromDate(),
                                        window.toDate(),
                                        DownloadTaskStatus.PENDING,
                                        0L,
                                        null,
                                        null
                                ));
                            }
                        }
                    }
                }
            }
        }
        return tasks;
    }

    public static long estimatedTaskCount(RollingOptionDownloadConfig config) {
        int windows = DhanHistoricalDataClient.splitDateWindows(
                config.fromDate(),
                config.toDate(),
                DhanProtocolConstants.ROLLING_OPTION_MAX_DAYS
        ).size();
        return (long) config.symbols().size()
                * config.expiries().size()
                * config.strikes().size()
                * config.optionTypes().size()
                * config.intervalMinutes().size()
                * windows;
    }
}
