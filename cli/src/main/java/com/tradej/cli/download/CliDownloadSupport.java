package com.tradej.cli.download;

import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.historical.ingest.model.DownloadJobStats;
import com.tradej.historical.ingest.model.EquityHistoricalDownloadConfig;
import com.tradej.historical.ingest.model.RollingOptionDownloadConfig;
import com.tradej.historical.ingest.planner.EquityHistoricalDownloadPlanner;
import com.tradej.historical.ingest.service.DownloadJobService;
import com.tradej.historical.ingest.service.EquityDownloadJobService;
import com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse;
import com.tradej.historical.ingest.universe.HistoricalEquityPaths;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CliDownloadSupport {

    private CliDownloadSupport() {
    }

    public static RollingOptionDownloadConfig parseRollingOptionConfig(
            String symbolsCsv,
            String segmentName,
            LocalDate from,
            LocalDate to,
            String intervalsCsv,
            String expiriesCsv,
            String strikesSpec,
            String optionTypesCsv,
            long delayMs
    ) {
        List<String> symbols = splitCsv(symbolsCsv).stream().map(ContractSymbolNormalizer::normalize).toList();
        List<Integer> intervals = splitCsv(intervalsCsv).stream().map(Integer::parseInt).toList();
        List<RollingExpiryRoll> expiries = splitCsv(expiriesCsv).stream().map(RollingExpiryRoll::parse).toList();
        List<StrikeOffset> strikes = parseStrikes(strikesSpec);
        List<OptionType> optionTypes = splitCsv(optionTypesCsv).stream().map(OptionType::fromCode).toList();
        ExchangeSegment segment = ExchangeSegment.valueOf(segmentName.trim().toUpperCase(Locale.ROOT));
        return new RollingOptionDownloadConfig(
                symbols,
                segment,
                from,
                to,
                intervals,
                expiries,
                strikes,
                optionTypes,
                delayMs,
                true
        );
    }

    public static DownloadJobService openService(
            Path warehousePath,
            com.tradej.broker.api.port.OptionsProvider optionsProvider,
            long delayMs,
            int workers
    ) {
        DuckDbHistoricalWarehouse warehouse = new DuckDbHistoricalWarehouse(warehousePath);
        return new DownloadJobService(
                warehouse,
                optionsProvider,
                workers,
                System::currentTimeMillis,
                () -> sleep(delayMs)
        );
    }

    public static Map<String, Object> startRollingOptions(
            DownloadJobService service,
            RollingOptionDownloadConfig config,
            boolean runImmediately
    ) throws Exception {
        String jobId = service.startRollingOptionJob(config);
        if (!runImmediately) {
            return Map.of("jobId", jobId, "status", "PENDING");
        }
        DownloadJobStats stats = service.runJob(jobId);
        return Map.of(
                "jobId", jobId,
                "status", stats.failedTasks() > 0 ? "FAILED" : "COMPLETED",
                "stats", stats
        );
    }

    public static EquityHistoricalDownloadConfig parseEquityConfig(
            String universeOrSymbols,
            String segmentName,
            LocalDate from,
            LocalDate to,
            String interval,
            String rootPath,
            long delayMs,
            int workers,
            boolean refreshUniverse
    ) {
        List<String> symbols;
        if (universeOrSymbols == null || universeOrSymbols.isBlank()
                || "nifty500".equalsIgnoreCase(universeOrSymbols.trim())) {
            symbols = List.of("NIFTY500");
        } else {
            symbols = splitCsv(universeOrSymbols).stream().map(ContractSymbolNormalizer::normalize).toList();
        }
        ExchangeSegment segment = ExchangeSegment.valueOf(segmentName.trim().toUpperCase(Locale.ROOT));
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(89) : from;
        return new EquityHistoricalDownloadConfig(
                symbols,
                segment,
                start,
                end,
                interval == null || interval.isBlank() ? "1m" : interval,
                "interval=1m",
                1,
                rootPath == null || rootPath.isBlank() ? HistoricalEquityPaths.DEFAULT_ROOT : rootPath,
                delayMs,
                workers,
                refreshUniverse,
                true
        );
    }

    public static EquityDownloadJobService openEquityService(
            Path rootPath,
            com.tradej.broker.api.port.MarketDataProvider marketDataProvider,
            com.tradej.broker.api.port.InstrumentResolver instrumentResolver,
            long delayMs,
            int workers,
            String universeUrl,
            com.tradej.historical.ingest.canonical.ParquetWriteService parquetWriteService
    ) {
        return new EquityDownloadJobService(
                rootPath,
                marketDataProvider,
                instrumentResolver,
                workers,
                System::currentTimeMillis,
                () -> sleep(delayMs),
                universeUrl,
                parquetWriteService
        );
    }

    /**
     * Create a ParquetWriteService for the given equity root path.
     * Resolves the canonical data root as the parent of the equity root.
     */
    public static com.tradej.historical.ingest.canonical.ParquetWriteService createParquetWriter(Path equityRoot) {
        Path dataRoot = equityRoot.getParent();
        return new com.tradej.historical.ingest.canonical.CanonicalBarWriter(dataRoot);
    }

    public static Map<String, Object> startEquityDownload(
            EquityDownloadJobService service,
            EquityHistoricalDownloadConfig config,
            boolean runImmediately
    ) throws Exception {
        long estimated = EquityHistoricalDownloadPlanner.estimatedTaskCount(config);
        String jobId = service.startEquityJob(config);
        if (!runImmediately) {
            return Map.of("jobId", jobId, "status", "PENDING", "estimatedTasks", estimated);
        }
        DownloadJobStats stats = service.runJob(jobId);
        return Map.of(
                "jobId", jobId,
                "status", stats.failedTasks() > 0 ? "FAILED" : "COMPLETED",
                "estimatedTasks", estimated,
                "stats", stats
        );
    }

    public static Path equityMetaDatabase(String rootPath) {
        return HistoricalEquityPaths.metaDatabase(Path.of(
                rootPath == null || rootPath.isBlank() ? HistoricalEquityPaths.DEFAULT_ROOT : rootPath));
    }

    private static List<StrikeOffset> parseStrikes(String strikesSpec) {
        if (strikesSpec == null || strikesSpec.isBlank() || "atm±10".equalsIgnoreCase(strikesSpec.replace(" ", ""))) {
            return StrikeOffset.atmPlusMinus(10);
        }
        if (strikesSpec.toLowerCase(Locale.ROOT).startsWith("atm±")) {
            int n = Integer.parseInt(strikesSpec.substring(4).trim());
            return StrikeOffset.atmPlusMinus(n);
        }
        return splitCsv(strikesSpec).stream().map(StrikeOffset::parseSpec).toList();
    }

    private static List<String> splitCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
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
