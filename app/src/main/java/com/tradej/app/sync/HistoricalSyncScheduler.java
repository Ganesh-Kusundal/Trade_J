package com.tradej.app.sync;

import com.tradej.app.config.TradingProperties;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.historical.ingest.calendar.CompositeHolidayCalendar;
import com.tradej.historical.ingest.sync.DataGapScanService;
import com.tradej.historical.ingest.sync.IncrementalSyncService;
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

    public HistoricalSyncScheduler(
            TradingProperties properties,
            CompositeHolidayCalendar calendar,
            DataGapScanService gapScanner,
            SyncStatusStore statusStore,
            @Qualifier("canonicalDataRoot") Path dataRoot,
            @Autowired(required = false) IncrementalSyncService syncService,
            @Qualifier("historicalDownloadExecutor") ExecutorService executor) {
        this.config = properties.sync();
        this.calendar = calendar;
        this.gapScanner = gapScanner;
        this.statusStore = statusStore;
        this.dataRoot = dataRoot;
        this.syncService = syncService;
        this.executor = executor;
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
        }
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

        log.info("Syncing {} symbols × {} dates using {} workers",
                symbols.size(), missingDates.size(), config.batchSize());

        for (LocalDate date : missingDates) {
            statusStore.recordDateSyncStart(date);
            try {
                int synced = syncDateParallel(segment, symbols, date);
                statusStore.recordDateSyncComplete(date, synced + " bars synced");
                log.info("Synced {} for {} ({} symbols, {} bars)", date, segment, symbols.size(), synced);
            } catch (Exception ex) {
                log.warn("Sync failed for {}: {}", date, ex.getMessage());
                statusStore.recordDateSyncFailed(date, ex.getMessage());
            }
        }

        statusStore.completeRun("Completed " + missingDates.size() + " dates");
        log.info("Sync completed — {} dates processed", missingDates.size());
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
}
