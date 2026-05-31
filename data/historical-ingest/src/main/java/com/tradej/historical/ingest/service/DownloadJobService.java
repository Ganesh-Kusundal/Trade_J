package com.tradej.historical.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.instrument.RollingExpiryKind;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.RollingOptionSeriesKey;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.model.RollingOptionSeries;
import com.tradej.core.domain.value.OptionType;
import com.tradej.historical.ingest.model.DownloadJobRecord;
import com.tradej.historical.ingest.model.DownloadJobStats;
import com.tradej.historical.ingest.model.DownloadJobStatus;
import com.tradej.historical.ingest.model.DownloadSourceType;
import com.tradej.historical.ingest.model.DownloadTaskRecord;
import com.tradej.historical.ingest.model.DownloadTaskStatus;
import com.tradej.historical.ingest.model.RollingOptionDownloadConfig;
import com.tradej.historical.ingest.planner.RollingOptionDownloadPlanner;
import com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class DownloadJobService {

    private static final Logger log = LoggerFactory.getLogger(DownloadJobService.class);

    private final DuckDbHistoricalWarehouse warehouse;
    private final OptionsProvider optionsProvider;
    private final RollingOptionDownloadPlanner planner;
    private final ObjectMapper objectMapper;
    private final LongSupplier clock;
    private final Runnable delayBetweenCalls;
    private final int workers;

    public DownloadJobService(
            DuckDbHistoricalWarehouse warehouse,
            OptionsProvider optionsProvider
    ) {
        this(warehouse, optionsProvider, 1, System::currentTimeMillis, () -> sleep(350L));
    }

    public DownloadJobService(
            DuckDbHistoricalWarehouse warehouse,
            OptionsProvider optionsProvider,
            int workers,
            LongSupplier clock,
            Runnable delayBetweenCalls
    ) {
        this.warehouse = warehouse;
        this.optionsProvider = optionsProvider;
        this.workers = Math.max(1, workers);
        this.planner = new RollingOptionDownloadPlanner();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.clock = clock;
        this.delayBetweenCalls = delayBetweenCalls;
    }

    public String startRollingOptionJob(RollingOptionDownloadConfig config) throws Exception {
        String jobId = UUID.randomUUID().toString();
        long now = clock.getAsLong();
        warehouse.insertJob(new DownloadJobRecord(
                jobId,
                DownloadSourceType.ROLLING_OPTION,
                DownloadJobStatus.PENDING,
                objectMapper.writeValueAsString(config),
                now,
                null,
                null,
                null
        ));
        warehouse.insertTasks(planner.planTasks(jobId, config));
        log.info("Created rolling option download job {} with {} estimated tasks",
                jobId, RollingOptionDownloadPlanner.estimatedTaskCount(config));
        return jobId;
    }

    public DownloadJobStats runJob(String jobId) throws Exception {
        Optional<DownloadJobRecord> job = warehouse.findJob(jobId);
        if (job.isEmpty()) {
            throw new IllegalArgumentException("Unknown job " + jobId);
        }
        RollingOptionDownloadConfig config = objectMapper.readValue(
                job.get().configJson(),
                RollingOptionDownloadConfig.class
        );

        long startedAt = clock.getAsLong();
        warehouse.updateJobStatus(jobId, DownloadJobStatus.RUNNING, startedAt, null, null);

        int reset = warehouse.resetStaleRunningTasks(jobId);
        if (reset > 0) {
            log.info("Reset {} stale RUNNING tasks to PENDING for job {}", reset, jobId);
        }

        AtomicLong processed = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(workers, r -> {
            Thread thread = new Thread(r, "historical-download-worker");
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

        DownloadJobStats stats = warehouse.jobStats(jobId);
        DownloadJobStatus finalStatus = stats.failedTasks() > 0L
                ? DownloadJobStatus.FAILED
                : DownloadJobStatus.COMPLETED;
        warehouse.updateJobStatus(
                jobId,
                finalStatus,
                startedAt,
                clock.getAsLong(),
                objectMapper.writeValueAsString(stats)
        );
        log.info("Job {} finished with status {} — completed={}, failed={}, rows={}",
                jobId, finalStatus, stats.completedTasks(), stats.failedTasks(), stats.rowsWritten());
        return stats;
    }

    public DownloadJobStats resumeJob(String jobId) throws Exception {
        return runJob(jobId);
    }

    public Optional<DownloadJobRecord> job(String jobId) throws Exception {
        return warehouse.findJob(jobId);
    }

    public DownloadJobStats stats(String jobId) throws Exception {
        return warehouse.jobStats(jobId);
    }

    public void resetWarehouse() throws Exception {
        warehouse.truncateDownloadData();
    }

    private void runWorker(String jobId, RollingOptionDownloadConfig config, AtomicLong processed) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Optional<DownloadTaskRecord> claimed = warehouse.claimNextTask(jobId);
                if (claimed.isEmpty()) {
                    return;
                }
                processTask(claimed.get(), config, processed);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Download worker failed for job " + jobId, ex);
        }
    }

    private void processTask(
            DownloadTaskRecord task,
            RollingOptionDownloadConfig config,
            AtomicLong processed
    ) throws Exception {
        try {
            if (workers <= 1) {
                delayBetweenCalls.run();
            }
            RollingOptionSeriesKey seriesKey = new RollingOptionSeriesKey(
                    task.underlying(),
                    config.exchangeSegment(),
                    new RollingExpiryRoll(RollingExpiryKind.fromCode(task.expiryKind()), task.expiryCode()),
                    new StrikeOffset(task.strikeOffset()),
                    OptionType.fromCode(task.optionType()),
                    task.intervalMin()
            );
            RollingOptionHistoryRequest request = new RollingOptionHistoryRequest(
                    seriesKey,
                    task.chunkFrom(),
                    task.chunkTo()
            );
            RollingOptionSeries series = optionsProvider.getExpiredOptionHistory(request);
            long rows = warehouse.upsertRollingOptionBars(
                    task.underlying(),
                    task.expiryKind(),
                    task.expiryCode(),
                    task.strikeOffset(),
                    task.optionType(),
                    task.intervalMin(),
                    series.bars()
            );
            warehouse.updateTask(task.taskId(), DownloadTaskStatus.COMPLETED, rows, null, clock.getAsLong());
            long count = processed.incrementAndGet();
            if (count % 50 == 0) {
                DownloadJobStats stats = warehouse.jobStats(task.jobId());
                log.info("Job {} progress: {}/{} tasks completed, {} rows written",
                        task.jobId(), stats.completedTasks(), stats.totalTasks(), stats.rowsWritten());
            }
        } catch (Exception ex) {
            log.warn("Task {} failed: {}", task.taskId(), ex.getMessage());
            warehouse.updateTask(
                    task.taskId(),
                    DownloadTaskStatus.FAILED,
                    0L,
                    ex.getMessage(),
                    clock.getAsLong()
            );
        }
    }

    private static void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
