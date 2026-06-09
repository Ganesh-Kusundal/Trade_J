package com.tradej.cli.command;

import com.tradej.cli.CliContext;
import com.tradej.cli.download.CliDownloadSupport;
import com.tradej.cli.download.CliEquityImportSupport;
import com.tradej.cli.output.OutputFormatter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class CliDownloadCommands extends CliCommandSupport {

    public CliDownloadCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    public void downloadRollingOptions(
            String symbols,
            String segment,
            LocalDate from,
            LocalDate to,
            String intervals,
            String expiry,
            String strikes,
            String optionTypes,
            String warehousePath,
            long delayMs,
            int workers,
            boolean runImmediately
    ) throws Exception {
        requireStandaloneDhan();
        session().ensureCatalogLoaded();
        var config = CliDownloadSupport.parseRollingOptionConfig(
                symbols, segment, from, to, intervals, expiry, strikes, optionTypes, delayMs
        );
        long estimated = com.tradej.historical.ingest.planner.RollingOptionDownloadPlanner.estimatedTaskCount(config);
        out().println("Estimated API tasks: " + estimated);
        if (!runImmediately) {
            try (var warehouse = new com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse(Path.of(warehousePath))) {
                String jobId = UUID.randomUUID().toString();
                warehouse.insertJob(new com.tradej.historical.ingest.model.DownloadJobRecord(
                        jobId,
                        com.tradej.historical.ingest.model.DownloadSourceType.ROLLING_OPTION,
                        com.tradej.historical.ingest.model.DownloadJobStatus.PENDING,
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                                .writeValueAsString(config),
                        System.currentTimeMillis(),
                        null,
                        null,
                        null
                ));
                warehouse.insertTasks(new com.tradej.historical.ingest.planner.RollingOptionDownloadPlanner().planTasks(jobId, config));
                out().print(Map.of("jobId", jobId, "status", "PENDING", "estimatedTasks", estimated));
            }
            return;
        }
        var service = CliDownloadSupport.openService(Path.of(warehousePath), options(), delayMs, workers);
        out().print(CliDownloadSupport.startRollingOptions(service, config, true));
    }

    public void downloadReset(String warehousePath) throws Exception {
        try (var warehouse = new com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse(Path.of(warehousePath))) {
            warehouse.truncateDownloadData();
            out().print(Map.of(
                    "warehouse", warehousePath,
                    "status", "RESET",
                    "message", "Truncated rolling_option_bars, download_tasks, download_jobs"
            ));
        }
    }

    public void downloadStatus(String jobId, String warehousePath) throws Exception {
        Path equityMeta = CliDownloadSupport.equityMetaDatabase(warehousePath);
        if (Files.exists(equityMeta)) {
            try (var warehouse = new com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse(equityMeta)) {
                var job = warehouse.findJob(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job " + jobId));
                var stats = warehouse.jobStats(jobId);
                out().print(Map.of(
                        "jobId", job.jobId(),
                        "sourceType", job.sourceType(),
                        "status", job.status(),
                        "stats", stats
                ));
            }
            return;
        }
        try (var warehouse = new com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse(Path.of(warehousePath))) {
            var job = warehouse.findJob(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job " + jobId));
            var stats = warehouse.jobStats(jobId);
            out().print(Map.of(
                    "jobId", job.jobId(),
                    "status", job.status(),
                    "stats", stats
            ));
        }
    }

    public void downloadResume(String jobId, String warehousePath, long delayMs, int workers) throws Exception {
        Path equityMeta = CliDownloadSupport.equityMetaDatabase(warehousePath);
        if (Files.exists(equityMeta)) {
            requireStandaloneUpstox();
            session().ensureCatalogLoaded();
            try (var service = CliDownloadSupport.openEquityService(
                    Path.of(warehousePath),
                    marketData(),
                    session().connection().instruments(),
                    delayMs,
                    workers,
                    com.tradej.historical.ingest.universe.Nifty500UniverseFetcher.DEFAULT_UNIVERSE_URL,
                    CliDownloadSupport.createParquetWriter(Path.of(warehousePath))
            )) {
                var stats = service.resumeJob(jobId);
                out().print(Map.of("jobId", jobId, "stats", stats));
            }
            return;
        }
        requireStandaloneDhan();
        session().ensureCatalogLoaded();
        var service = CliDownloadSupport.openService(Path.of(warehousePath), options(), delayMs, workers);
        var stats = service.resumeJob(jobId);
        out().print(Map.of("jobId", jobId, "stats", stats));
    }

    public void refreshNifty500Universe(String rootPath) throws Exception {
        requireStandaloneUpstox();
        session().ensureCatalogLoaded();
        try (var service = CliDownloadSupport.openEquityService(
                Path.of(rootPath),
                marketData(),
                session().connection().instruments(),
                0L,
                1,
                com.tradej.historical.ingest.universe.Nifty500UniverseFetcher.DEFAULT_UNIVERSE_URL,
                CliDownloadSupport.createParquetWriter(Path.of(rootPath))
        )) {
            out().print(service.refreshUniverse());
        }
    }

    public void refreshUniverseFromDisk(String rootPath) throws Exception {
        int count = new com.tradej.historical.ingest.maintenance.UniverseDiskRefresher(Path.of(rootPath))
                .refreshFromBars();
        out().print(Map.of("root", rootPath, "symbolsWritten", count));
    }

    public void compactEquityPartitions(String rootPath) throws Exception {
        var result = new com.tradej.historical.ingest.maintenance.EquityParquetCompactor(Path.of(rootPath)).compact();
        out().print(Map.of(
                "root", rootPath,
                "symbolsProcessed", result.symbolsProcessed(),
                "taskFilesMerged", result.taskFilesMerged(),
                "partitionsWritten", result.partitionsWritten()
        ));
    }

    public void analyticsCatalog(String equityRoot, String optionsWarehouse) throws Exception {
        try (var service = com.tradej.cli.analytics.CliAnalyticsSupport.openService(
                equityRoot, optionsWarehouse, "", false)) {
            out().print(com.tradej.cli.analytics.CliAnalyticsSupport.catalog(service));
        }
    }

    public void analyticsSql(String equityRoot, String optionsWarehouse, String sql, int limit) throws Exception {
        try (var service = com.tradej.cli.analytics.CliAnalyticsSupport.openService(
                equityRoot, optionsWarehouse, "", false)) {
            out().print(com.tradej.cli.analytics.CliAnalyticsSupport.sql(service, sql, limit));
        }
    }

    public void analyticsQueryEquity(
            String equityRoot,
            String optionsWarehouse,
            String symbol,
            String interval,
            LocalDate from,
            LocalDate to
    ) throws Exception {
        try (var service = com.tradej.cli.analytics.CliAnalyticsSupport.openService(
                equityRoot, optionsWarehouse, "", false)) {
            out().print(com.tradej.cli.analytics.CliAnalyticsSupport.equityCandles(
                    service, symbol, interval, from, to));
        }
    }

    public void analyticsQueryOptions(
            String equityRoot,
            String optionsWarehouse,
            String underlying,
            String expiryKind,
            int expiryCode,
            int strikeOffset,
            String optionType,
            int intervalMin,
            long fromMs,
            long toMs,
            int limit
    ) throws Exception {
        try (var service = com.tradej.cli.analytics.CliAnalyticsSupport.openService(
                equityRoot, optionsWarehouse, "", false)) {
            out().print(com.tradej.cli.analytics.CliAnalyticsSupport.optionBars(
                    service, underlying, expiryKind, expiryCode, strikeOffset,
                    optionType, intervalMin, fromMs, toMs, limit));
        }
    }

    public void downloadJobsList(String source, String equityRoot, String optionsWarehouse, int limit) throws Exception {
        try (var registry = openDownloadJobRegistry(equityRoot, optionsWarehouse)) {
            var sourceType = com.tradej.historical.ingest.model.DownloadSourceType.valueOf(source.toUpperCase());
            out().print(registry.listJobs(sourceType, limit));
        }
    }

    private com.tradej.historical.ingest.service.DownloadJobRegistry openDownloadJobRegistry(
            String equityRoot,
            String optionsWarehouse
    ) {
        return new com.tradej.historical.ingest.service.DownloadJobRegistry(
                Path.of(optionsWarehouse),
                Path.of(equityRoot)
        );
    }

    public void downloadEquity(
            String universeOrSymbols,
            String segment,
            LocalDate from,
            LocalDate to,
            String interval,
            String rootPath,
            long delayMs,
            int workers,
            boolean refreshUniverse,
            boolean runImmediately
    ) throws Exception {
        requireStandaloneUpstox();
        session().ensureCatalogLoaded();
        var config = CliDownloadSupport.parseEquityConfig(
                universeOrSymbols, segment, from, to, interval, rootPath, delayMs, workers, refreshUniverse
        );
        long estimated = com.tradej.historical.ingest.planner.EquityHistoricalDownloadPlanner.estimatedTaskCount(config);
        out().println("Estimated API tasks: " + estimated);
        try (var service = CliDownloadSupport.openEquityService(
                Path.of(config.rootPath()),
                marketData(),
                session().connection().instruments(),
                delayMs,
                workers,
                com.tradej.historical.ingest.universe.Nifty500UniverseFetcher.DEFAULT_UNIVERSE_URL,
                CliDownloadSupport.createParquetWriter(Path.of(config.rootPath()))
        )) {
            out().print(CliDownloadSupport.startEquityDownload(service, config, runImmediately));
        }
    }

    public void importEquityHive(
            String sourceHive,
            String universeCsv,
            String industryParquet,
            String rootPath,
            String fromMonth,
            String toMonth,
            boolean force,
            String symbols,
            boolean skipUniverseImport
    ) throws Exception {
        var result = CliEquityImportSupport.importHive(
                sourceHive,
                universeCsv,
                industryParquet,
                rootPath,
                fromMonth,
                toMonth,
                force,
                symbols,
                skipUniverseImport
        );
        out().print(CliEquityImportSupport.toResponseMap(result));
    }
}
