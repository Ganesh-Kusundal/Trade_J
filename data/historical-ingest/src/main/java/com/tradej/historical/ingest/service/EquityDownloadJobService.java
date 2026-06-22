package com.tradej.historical.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.historical.ingest.model.DownloadJobRecord;
import com.tradej.historical.ingest.model.DownloadJobStats;
import com.tradej.historical.ingest.model.DownloadJobStatus;
import com.tradej.historical.ingest.model.DownloadSourceType;
import com.tradej.historical.ingest.model.DownloadTaskRecord;
import com.tradej.historical.ingest.model.DownloadTaskStatus;
import com.tradej.historical.ingest.model.EquityHistoricalDownloadConfig;
import com.tradej.historical.ingest.planner.EquityHistoricalDownloadPlanner;
import com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse;
import com.tradej.historical.ingest.canonical.ParquetWriteService;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.historical.ingest.universe.Nifty500UniverseFetcher;
import com.tradej.historical.ingest.universe.UniverseRefreshService;
import com.tradej.historical.ingest.universe.UniverseSnapshotWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class EquityDownloadJobService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EquityDownloadJobService.class);

    private final DuckDbHistoricalWarehouse metaStore;
    private final MarketDataProvider marketDataProvider;
    private final InstrumentResolver instrumentResolver;
    private final ParquetWriteService parquetWriter;
    private final UniverseRefreshService universeRefreshService;
    private final EquityHistoricalDownloadPlanner planner;
    private final ObjectMapper objectMapper;
    private final LongSupplier clock;
    private final Runnable delayBetweenCalls;
    private final int workers;

    public EquityDownloadJobService(
            Path rootPath,
            MarketDataProvider marketDataProvider,
            InstrumentResolver instrumentResolver,
            int workers,
            LongSupplier clock,
            Runnable delayBetweenCalls,
            String universeUrl,
            ParquetWriteService parquetWriter
    ) {
        Path resolvedRoot = HistoricalEquityPaths.root(rootPath);
        this.metaStore = new DuckDbHistoricalWarehouse(HistoricalEquityPaths.metaDatabase(resolvedRoot));
        this.marketDataProvider = marketDataProvider;
        this.instrumentResolver = instrumentResolver;
        this.parquetWriter = parquetWriter;
        this.universeRefreshService = new UniverseRefreshService(
                new Nifty500UniverseFetcher(java.net.http.HttpClient.newHttpClient(), universeUrl),
                new UniverseSnapshotWriter(),
                resolvedRoot
        );
        this.planner = new EquityHistoricalDownloadPlanner();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.clock = clock;
        this.delayBetweenCalls = delayBetweenCalls;
        this.workers = Math.max(1, workers);
    }

    public UniverseRefreshService.UniverseRefreshResult refreshUniverse() throws Exception {
        return universeRefreshService.refresh(instrumentResolver);
    }

    public String startEquityJob(EquityHistoricalDownloadConfig config) throws Exception {
        EquityHistoricalDownloadConfig effective = maybeRefreshUniverse(config);
        String jobId = UUID.randomUUID().toString();
        long now = clock.getAsLong();
        metaStore.insertJob(new DownloadJobRecord(
                jobId,
                DownloadSourceType.EQUITY_INTRADAY,
                DownloadJobStatus.PENDING,
                objectMapper.writeValueAsString(effective),
                now,
                null,
                null,
                null
        ));
        metaStore.insertTasks(planner.planTasks(jobId, effective));
        log.info("Created equity download job {} with {} tasks", jobId, planner.estimatedTaskCount(effective));
        return jobId;
    }

    public DownloadJobStats runJob(String jobId) throws Exception {
        Optional<DownloadJobRecord> job = metaStore.findJob(jobId);
        if (job.isEmpty()) {
            throw new IllegalArgumentException("Unknown job " + jobId);
        }
        EquityHistoricalDownloadConfig config = objectMapper.readValue(
                job.get().configJson(),
                EquityHistoricalDownloadConfig.class
        );

        long startedAt = clock.getAsLong();
        metaStore.updateJobStatus(jobId, DownloadJobStatus.RUNNING, startedAt, null, null);

        int reset = metaStore.resetStaleRunningTasks(jobId);
        if (reset > 0) {
            log.info("Reset {} stale RUNNING equity tasks to PENDING for job {}", reset, jobId);
        }

        AtomicLong processed = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(workers, r -> {
            Thread thread = new Thread(r, "equity-download-worker");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> runWorker(jobId, config, processed)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        DownloadJobStats stats = metaStore.jobStats(jobId);
        DownloadJobStatus finalStatus = stats.failedTasks() > 0L
                ? DownloadJobStatus.FAILED
                : DownloadJobStatus.COMPLETED;
        metaStore.updateJobStatus(
                jobId,
                finalStatus,
                startedAt,
                clock.getAsLong(),
                objectMapper.writeValueAsString(stats)
        );
        log.info("Equity job {} finished with status {} — completed={}, failed={}, rows={}",
                jobId, finalStatus, stats.completedTasks(), stats.failedTasks(), stats.rowsWritten());
        return stats;
    }

    public DownloadJobStats resumeJob(String jobId) throws Exception {
        return runJob(jobId);
    }

    public Optional<DownloadJobRecord> job(String jobId) throws Exception {
        return metaStore.findJob(jobId);
    }

    public DownloadJobStats stats(String jobId) throws Exception {
        return metaStore.jobStats(jobId);
    }

    @Override
    public void close() throws Exception {
        metaStore.close();
    }

    private EquityHistoricalDownloadConfig maybeRefreshUniverse(EquityHistoricalDownloadConfig config) throws Exception {
        if (!config.refreshUniverseOnStart()) {
            return config;
        }
        UniverseRefreshService.UniverseRefreshResult refresh = universeRefreshService.refresh(instrumentResolver);
        if (config.symbols().size() == 1 && "NIFTY500".equalsIgnoreCase(config.symbols().getFirst())) {
            List<String> symbols = loadUniverseSymbols(refresh.rootPath());
            return new EquityHistoricalDownloadConfig(
                    symbols,
                    config.exchangeSegment(),
                    config.fromDate(),
                    config.toDate(),
                    config.interval(),
                    config.intervalFolder(),
                    config.intervalMinutes(),
                    config.rootPath(),
                    config.delayMs(),
                    config.workers(),
                    false,
                    config.resume()
            );
        }
        return config;
    }

    static List<String> loadUniverseSymbols(Path rootPath) throws Exception {
        try (var query = new com.tradej.historical.ingest.query.EquityHistoricalQuery(rootPath)) {
            return query.queryUniverse().stream()
                    .map(row -> String.valueOf(row.get("symbol")))
                    .toList();
        }
    }

    private void runWorker(String jobId, EquityHistoricalDownloadConfig config, AtomicLong processed) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Optional<DownloadTaskRecord> claimed = metaStore.claimNextTask(jobId);
                if (claimed.isEmpty()) {
                    return;
                }
                processTask(claimed.get(), config, processed);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Equity download worker failed for job " + jobId, ex);
        }
    }

    private void processTask(
            DownloadTaskRecord task,
            EquityHistoricalDownloadConfig config,
            AtomicLong processed
    ) throws Exception {
        try {
            delayBetweenCalls.run();
            InstrumentKey key = InstrumentKey.of(task.underlying(), config.exchangeSegment());
            var candles = marketDataProvider.getCandles(new CandleHistoryRequest(
                    key,
                    config.interval(),
                    task.chunkFrom(),
                    task.chunkTo()
            ));
            parquetWriter.writeBars(
                    config.exchangeSegment().name(),
                    task.underlying(),
                    config.interval(),
                    candles
            );
            metaStore.updateTask(
                    task.taskId(),
                    DownloadTaskStatus.COMPLETED,
                    candles.size(),
                    null,
                    clock.getAsLong()
            );
            long count = processed.incrementAndGet();
            if (count % 25 == 0) {
                DownloadJobStats stats = metaStore.jobStats(task.jobId());
                log.info("Equity job {} progress: {}/{} tasks completed, {} rows written",
                        task.jobId(), stats.completedTasks(), stats.totalTasks(), stats.rowsWritten());
            }
        } catch (Exception ex) {
            log.warn("Equity task {} failed: {}", task.taskId(), ex.getMessage());
            metaStore.updateTask(
                    task.taskId(),
                    DownloadTaskStatus.FAILED,
                    0L,
                    ex.getMessage(),
                    clock.getAsLong()
            );
        }
    }

    private static String normalizeStoredInterval(String interval) {
        return switch (interval.trim().toLowerCase()) {
            case "1m", "1minute" -> "1minute";
            case "30m", "30minute", "1h", "60m" -> "30minute";
            case "1d", "d", "day" -> "day";
            default -> interval;
        };
    }
}
