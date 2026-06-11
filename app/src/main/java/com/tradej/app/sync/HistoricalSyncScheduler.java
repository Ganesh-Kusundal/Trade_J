package com.tradej.app.sync;

import com.tradej.app.config.TradingProperties;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.calendar.CompositeHolidayCalendar;
import com.tradej.historical.ingest.sync.DataGapScanService;
import com.tradej.historical.ingest.sync.IncrementalSyncService;
import com.tradej.historical.ingest.sync.RuntimeParquetExporter;
import com.tradej.historical.ingest.service.DownloadJobService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Automatic historical data sync scheduler.
 *
 * <p>Runs daily after market close (default 16:00 IST) to:
 * <ol>
 *   <li>Refresh holiday calendar from data</li>
 *   <li>Scan for gaps in the last N months via {@link DataGapScanService}</li>
 *   <li>Fetch missing data via {@link IncrementalSyncService} (broker-abstracted,
 *       routed through {@code BrokerRouter} — works with Dhan, Upstox, ICICI)</li>
 *   <li>Parallelize across symbols using the download executor pool</li>
 * </ol>
 *
 * <p>Can also be triggered manually via REST API or CLI.
 */
@Component
@ConditionalOnProperty(name = "trade.sync.enabled", havingValue = "true", matchIfMissing = true)
public class HistoricalSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(HistoricalSyncScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final TradingProperties.SyncProperties config;
    private final CompositeHolidayCalendar calendar;
    private final DataGapScanService gapScanner;
    private final SyncStatusStore statusStore;
    private final Path dataRoot;
    private final IncrementalSyncService syncService;
    private final ExecutorService executor;
    private final RuntimeParquetExporter runtimeExporter;
    private final DownloadJobService downloadJobService;

    public HistoricalSyncScheduler(
            TradingProperties properties,
            CompositeHolidayCalendar calendar,
            DataGapScanService gapScanner,
            SyncStatusStore statusStore,
            @Qualifier("canonicalDataRoot") Path dataRoot,
            @Autowired(required = false) IncrementalSyncService syncService,
            @Qualifier("historicalDownloadExecutor") ExecutorService executor,
            @Autowired(required = false) RuntimeParquetExporter runtimeExporter,
            @Autowired(required = false) DownloadJobService downloadJobService) {
        this.config = properties.sync();
        this.calendar = calendar;
        this.gapScanner = gapScanner;
        this.statusStore = statusStore;
        this.dataRoot = dataRoot;
        this.syncService = syncService;
        this.executor = executor;
        this.runtimeExporter = runtimeExporter;
        this.downloadJobService = downloadJobService;
    }

    @PostConstruct
    void startupCheck() {
        if (!config.enabled()) {
            return;
        }
        try {
            var report = gapScanner.scan(config.segment(), "1m", config.lookbackMonths());
            if (report.daysComplete() == 0 && report.daysMissing() > 0) {
                log.info("No historical data found — triggering initial sync for last {} months ({} missing dates)",
                        config.lookbackMonths(), report.daysMissing());
                syncGapDates(report.allGapDates());
            }
        } catch (Exception ex) {
            log.debug("Startup data check skipped: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "${trade.sync.cron:0 0 16 * * MON-FRI}", zone = "Asia/Kolkata")
    public void dailySync() {
        if (!config.enabled()) {
            log.debug("Sync scheduler disabled");
            return;
        }

        log.info("Starting daily sync — segment={}, lookback={} months", config.segment(), config.lookbackMonths());
        statusStore.startNewRun("daily");

        try {
            if (config.refreshHolidays()) {
                calendar.refreshFromData(dataRoot);
                statusStore.recordHolidayRefresh();
            }

            var report = gapScanner.scan(config.segment(), "1m", config.lookbackMonths());
            statusStore.recordGapScan(report);

            if (report.isFullyComplete()) {
                log.info("No gaps detected — sync complete");
                statusStore.completeRun("No gaps");
                return;
            }

            syncGapDates(report.allGapDates());

        } catch (Exception ex) {
            log.error("Daily sync failed", ex);
            statusStore.failRun(ex.getMessage());
            return;
        }

        exportRuntimeCandles();
    }

    public void triggerSync(LocalDate from, LocalDate to) {
        log.info("Manual sync triggered: {} → {}", from, to);
        statusStore.startNewRun("manual");

        try {
            var report = gapScanner.scanRange(config.segment(), "1m", from, to);
            statusStore.recordGapScan(report);

            if (report.isFullyComplete()) {
                log.info("No gaps in range — nothing to sync");
                statusStore.completeRun("No gaps");
                return;
            }

            syncGapDates(report.allGapDates());

        } catch (Exception ex) {
            log.error("Manual sync failed", ex);
            statusStore.failRun(ex.getMessage());
        }
    }

    private void syncGapDates(List<LocalDate> missingDates) {
        if (syncService == null) {
            log.warn("IncrementalSyncService not available — no broker connected. "
                    + "Connect a broker (Dhan/Upstox/ICICI) to enable sync.");
            statusStore.recordSyncPlan(missingDates);
            statusStore.failRun("No broker connected");
            return;
        }

        log.info("Found {} gap dates: {}", missingDates.size(), missingDates);
        statusStore.recordSyncPlan(missingDates);

        ExchangeSegment segment;
        try {
            segment = ExchangeSegment.valueOf(config.segment());
        } catch (IllegalArgumentException ex) {
            segment = ExchangeSegment.NSE_EQ;
        }

        List<String> symbols = syncService.availableSymbols(segment);
        if (symbols.isEmpty()) {
            log.warn("No symbols available for sync");
            statusStore.failRun("No symbols");
            return;
        }

        log.info("Bulk syncing {} symbols over {} gap dates (90-day windows)", symbols.size(), missingDates.size());

        var result = syncService.syncAllRanges(symbols, segment, "1m", missingDates);

        statusStore.completeRun(String.format("Bulk sync: %d symbols, %d bars, %d API calls (%d failed)",
                result.symbolsSynced(), result.totalBarsWritten(), result.apiCalls(), result.failedCalls()));
        log.info("Sync completed — {} API calls for {} symbols (was {} calls with day-by-day approach)",
                result.apiCalls(), symbols.size(), symbols.size() * missingDates.size());
    }

    private int syncDateParallel(ExchangeSegment segment, List<String> symbols, LocalDate date) {
        AtomicInteger totalBars = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (String symbol : symbols) {
            futures.add(executor.submit(() -> {
                try {
                    var result = syncService.syncSymbol(symbol, segment, "1m", date);
                    totalBars.addAndGet(result.barsWritten());
                } catch (Exception ex) {
                    log.debug("Failed to sync {} for {}: {}", symbol, date, ex.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception ignored) {
            }
        }

        return totalBars.get();
    }

    public SyncStatusStore.SyncStatus getStatus() {
        return statusStore.getStatus();
    }

    private void exportRuntimeCandles() {
        if (runtimeExporter == null) {
            log.debug("RuntimeParquetExporter not available — skipping runtime export");
            return;
        }
        try {
            LocalDate today = LocalDate.now(IST);
            var result = runtimeExporter.exportDate(today);
            log.info("Runtime export for {}: {} symbols, {} bars, status={}",
                    today, result.symbolsExported(), result.barsExported(), result.status());
        } catch (Exception ex) {
            log.warn("Runtime parquet export failed: {}", ex.getMessage());
        }
    }

    public void syncAll() {
        log.info("=== Full sync-all started ===");
        statusStore.startNewRun("sync-all");

        try {
            if (config.refreshHolidays()) {
                calendar.refreshFromData(dataRoot);
                statusStore.recordHolidayRefresh();
            }

            var report = gapScanner.scan(config.segment(), "1m", config.lookbackMonths());
            statusStore.recordGapScan(report);

            if (!report.isFullyComplete()) {
                syncGapDates(report.allGapDates());
            } else {
                log.info("No equity gaps found");
            }

            exportRuntimeCandles();

            syncOptions();

            statusStore.completeRun("sync-all complete");
            log.info("=== Full sync-all completed ===");
        } catch (Exception ex) {
            log.error("sync-all failed", ex);
            statusStore.failRun(ex.getMessage());
        }
    }

    private void syncOptions() {
        if (downloadJobService == null) {
            log.debug("DownloadJobService not available — skipping options sync (no broker with OptionsProvider connected)");
            return;
        }
        try {
            log.info("Checking option download jobs...");
            var recentJobs = downloadJobService.listRecentJobs(5);

            var pendingJobs = recentJobs.stream()
                    .filter(j -> j.status() == com.tradej.historical.ingest.model.DownloadJobStatus.PENDING
                            || j.status() == com.tradej.historical.ingest.model.DownloadJobStatus.FAILED)
                    .toList();

            if (!pendingJobs.isEmpty()) {
                String jobId = pendingJobs.getFirst().jobId();
                log.info("Resuming option download job: {}", jobId);
                downloadJobService.resumeJob(jobId);
                return;
            }

            boolean hasRecentCompleted = recentJobs.stream()
                    .anyMatch(j -> j.status() == com.tradej.historical.ingest.model.DownloadJobStatus.COMPLETED
                            && j.finishedAtMs() != null
                            && j.finishedAtMs() > System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000);

            if (!hasRecentCompleted) {
                log.info("No recent completed option job — creating fresh 30-day rolling option download");
                var config = new com.tradej.historical.ingest.model.RollingOptionDownloadConfig(
                        List.of("NIFTY", "BANKNIFTY", "FINNIFTY"),
                        com.tradej.core.domain.value.ExchangeSegment.IDX_I,
                        LocalDate.now(IST).minusDays(30),
                        LocalDate.now(IST),
                        List.of(5),
                        List.of(
                                new com.tradej.core.domain.instrument.RollingExpiryRoll(
                                        com.tradej.core.domain.instrument.RollingExpiryKind.MONTH, 1),
                                new com.tradej.core.domain.instrument.RollingExpiryRoll(
                                        com.tradej.core.domain.instrument.RollingExpiryKind.WEEK, 1)
                        ),
                        com.tradej.core.domain.instrument.StrikeOffset.atmPlusMinus(2),
                        List.of(com.tradej.core.domain.value.OptionType.CALL,
                                com.tradej.core.domain.value.OptionType.PUT),
                        350L,
                        true
                );
                String jobId = downloadJobService.startRollingOptionJob(config);
                log.info("Created and running option download job: {}", jobId);
                downloadJobService.runJob(jobId);
            } else {
                log.info("Recent completed option job found — skipping fresh download");
            }
        } catch (Exception ex) {
            log.warn("Options sync failed: {}", ex.getMessage());
        }
    }
}
