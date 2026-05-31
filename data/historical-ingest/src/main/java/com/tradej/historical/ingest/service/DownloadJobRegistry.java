package com.tradej.historical.ingest.service;

import com.tradej.historical.ingest.model.DownloadJobRecord;
import com.tradej.historical.ingest.model.DownloadSourceType;
import com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Routes download job queries to the options warehouse or equity {@code _meta/jobs.duckdb}.
 */
public final class DownloadJobRegistry implements AutoCloseable {

    private final DuckDbHistoricalWarehouse optionsStore;
    private final DuckDbHistoricalWarehouse equityStore;
    private final Path equityRoot;

    public DownloadJobRegistry(Path optionsWarehousePath, Path equityRootPath) {
        this.equityRoot = HistoricalEquityPaths.root(equityRootPath);
        this.optionsStore = new DuckDbHistoricalWarehouse(optionsWarehousePath);
        Path equityMeta = HistoricalEquityPaths.metaDatabase(this.equityRoot);
        this.equityStore = Files.isRegularFile(equityMeta)
                ? new DuckDbHistoricalWarehouse(equityMeta)
                : null;
    }

    public List<DownloadJobRecord> listJobs(DownloadSourceType source, int limit) throws SQLException {
        DuckDbHistoricalWarehouse store = storeFor(source);
        if (store == null) {
            return List.of();
        }
        return store.listRecentJobs(limit).stream()
                .filter(job -> job.sourceType() == source)
                .toList();
    }

    public Optional<DownloadJobRecord> findJob(String jobId) throws SQLException {
        Optional<DownloadJobRecord> options = optionsStore.findJob(jobId);
        if (options.isPresent()) {
            return options;
        }
        if (equityStore == null) {
            return Optional.empty();
        }
        return equityStore.findJob(jobId);
    }

    public DownloadJobStatsView stats(String jobId) throws SQLException {
        DownloadJobRecord job = findJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job " + jobId));
        DuckDbHistoricalWarehouse store = storeFor(job.sourceType());
        if (store == null) {
            throw new IllegalStateException("No job store for source " + job.sourceType());
        }
        return new DownloadJobStatsView(job, store.jobStats(jobId));
    }

    public Path equityRoot() {
        return equityRoot;
    }

    public Path optionsWarehousePath() {
        return optionsStore.databasePath();
    }

    private DuckDbHistoricalWarehouse storeFor(DownloadSourceType source) {
        return switch (source) {
            case ROLLING_OPTION -> optionsStore;
            case EQUITY_INTRADAY -> equityStore;
        };
    }

    @Override
    public void close() throws Exception {
        optionsStore.close();
        if (equityStore != null) {
            equityStore.close();
        }
    }

    public record DownloadJobStatsView(
            DownloadJobRecord job,
            com.tradej.historical.ingest.model.DownloadJobStats stats
    ) {
    }
}
