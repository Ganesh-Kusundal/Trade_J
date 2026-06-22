package com.tradej.app.admin;

import com.tradej.app.config.TradingProperties;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.StandardInstrumentIdentityService;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;
import com.tradej.core.domain.model.UniverseEntry;
import com.tradej.core.domain.port.HistoricalAnalyticsService;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.historical.ingest.model.DownloadJobRecord;
import com.tradej.historical.ingest.model.DownloadJobStats;
import com.tradej.historical.ingest.model.DownloadSourceType;
import com.tradej.historical.ingest.importing.HiveCacheEquityImporter;
import com.tradej.historical.ingest.importing.HiveCacheImportConfig;
import com.tradej.historical.ingest.importing.HiveCacheImportResult;
import com.tradej.historical.ingest.model.EquityHistoricalDownloadConfig;
import com.tradej.historical.ingest.model.RollingOptionDownloadConfig;
import com.tradej.historical.ingest.planner.EquityHistoricalDownloadPlanner;
import com.tradej.historical.ingest.service.DownloadJobRegistry;
import com.tradej.historical.ingest.service.DownloadJobService;
import com.tradej.historical.ingest.service.EquityDownloadJobService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/admin")
public class HistoricalDownloadController {

    private static final StandardInstrumentIdentityService INSTRUMENT_IDENTITY =
            StandardInstrumentIdentityService.INSTANCE;

    private final DownloadJobService downloadJobService;
    private final ObjectProvider<EquityDownloadJobService> equityDownloadJobService;
    private final DownloadJobRegistry downloadJobRegistry;
    private final HistoricalAnalyticsService historicalAnalyticsService;
    private final ExecutorService historicalDownloadExecutor;
    private final HiveCacheEquityImporter hiveCacheEquityImporter;
    private final TradingProperties tradingProperties;

    public HistoricalDownloadController(
            DownloadJobService downloadJobService,
            ObjectProvider<EquityDownloadJobService> equityDownloadJobService,
            DownloadJobRegistry downloadJobRegistry,
            HistoricalAnalyticsService historicalAnalyticsService,
            ExecutorService historicalDownloadExecutor,
            HiveCacheEquityImporter hiveCacheEquityImporter,
            TradingProperties tradingProperties
    ) {
        this.downloadJobService = downloadJobService;
        this.equityDownloadJobService = equityDownloadJobService;
        this.downloadJobRegistry = downloadJobRegistry;
        this.historicalAnalyticsService = historicalAnalyticsService;
        this.historicalDownloadExecutor = historicalDownloadExecutor;
        this.hiveCacheEquityImporter = hiveCacheEquityImporter;
        this.tradingProperties = tradingProperties;
    }

    @PostMapping("/download/jobs")
    ResponseEntity<Map<String, Object>> startJob(@RequestBody RollingOptionJobRequest request) throws Exception {
        RollingOptionDownloadConfig config = toConfig(request);
        String jobId = downloadJobService.startRollingOptionJob(config);
        if (request.async()) {
            historicalDownloadExecutor.submit(() -> {
                try {
                    downloadJobService.runJob(jobId);
                } catch (Exception ex) {
                    throw new IllegalStateException("Download job " + jobId + " failed", ex);
                }
            });
            return ResponseEntity.accepted().body(Map.of(
                    "jobId", jobId,
                    "status", "RUNNING",
                    "estimatedTasks", request.estimatedTasks(config)
            ));
        }
        DownloadJobStats stats = downloadJobService.runJob(jobId);
        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", stats.failedTasks() > 0 ? "FAILED" : "COMPLETED",
                "stats", stats
        ));
    }

    @PostMapping("/download/jobs/equity")
    ResponseEntity<Map<String, Object>> startEquityJob(@RequestBody EquityJobRequest request) throws Exception {
        EquityDownloadJobService equityService = requireEquityService();
        EquityHistoricalDownloadConfig config = toEquityConfig(request);
        String jobId = equityService.startEquityJob(config);
        if (request.async()) {
            historicalDownloadExecutor.submit(() -> {
                try {
                    equityService.runJob(jobId);
                } catch (Exception ex) {
                    throw new IllegalStateException("Equity download job " + jobId + " failed", ex);
                }
            });
            return ResponseEntity.accepted().body(Map.of(
                    "jobId", jobId,
                    "status", "RUNNING",
                    "estimatedTasks", EquityHistoricalDownloadPlanner.estimatedTaskCount(config)
            ));
        }
        DownloadJobStats stats = equityService.runJob(jobId);
        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", stats.failedTasks() > 0 ? "FAILED" : "COMPLETED",
                "stats", stats
        ));
    }

    @PostMapping("/universe/nifty500/refresh")
    ResponseEntity<Map<String, Object>> refreshNifty500Universe() throws Exception {
        EquityDownloadJobService equityService = requireEquityService();
        var result = equityService.refreshUniverse();
        return ResponseEntity.ok(Map.of(
                "resolvedCount", result.resolvedCount(),
                "unresolvedCount", result.unresolvedCount(),
                "unresolvedSymbols", result.unresolvedSymbols(),
                "rootPath", result.rootPath().toString()
        ));
    }

    @GetMapping("/download/jobs")
    ResponseEntity<Map<String, Object>> listJobs(
            @RequestParam String source,
            @RequestParam(defaultValue = "20") int limit
    ) throws Exception {
        DownloadSourceType sourceType = DownloadSourceType.valueOf(source.trim().toUpperCase());
        List<DownloadJobRecord> jobs = downloadJobRegistry.listJobs(sourceType, limit);
        return ResponseEntity.ok(Map.of(
                "source", sourceType.name(),
                "count", jobs.size(),
                "jobs", jobs.stream().map(this::toJobSummary).toList()
        ));
    }

    @GetMapping("/download/jobs/{jobId}")
    ResponseEntity<Map<String, Object>> jobStatus(@PathVariable String jobId) throws Exception {
        DownloadJobRecord job = findJob(jobId);
        DownloadJobStats stats = jobStats(jobId, job);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.jobId());
        body.put("sourceType", job.sourceType());
        body.put("status", job.status());
        body.put("createdAtMs", job.createdAtMs());
        body.put("startedAtMs", job.startedAtMs());
        body.put("finishedAtMs", job.finishedAtMs());
        body.put("stats", stats);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/download/jobs/{jobId}/resume")
    ResponseEntity<Map<String, Object>> resumeJob(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "true") boolean async
    ) throws Exception {
        DownloadJobRecord job = findJob(jobId);
        if (async) {
            historicalDownloadExecutor.submit(() -> {
                try {
                    resumeJobInternal(jobId, job);
                } catch (Exception ex) {
                    throw new IllegalStateException("Resume job " + jobId + " failed", ex);
                }
            });
            return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "RUNNING"));
        }
        DownloadJobStats stats = resumeJobInternal(jobId, job);
        return ResponseEntity.ok(Map.of("jobId", jobId, "stats", stats));
    }

    @Deprecated(forRemoval = false)
    @GetMapping("/historical/rolling-options")
    ResponseEntity<Map<String, Object>> rollingOptions(
            @RequestParam String underlying,
            @RequestParam String expiryKind,
            @RequestParam int expiryCode,
            @RequestParam int strikeOffset,
            @RequestParam String optionType,
            @RequestParam(defaultValue = "5") int intervalMin,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "1000") int limit
    ) throws Exception {
        String normalizedUnderlying = StandardInstrumentIdentityService.INSTANCE.canonicalSymbol(underlying);
        List<RollingOptionBar> bars = historicalAnalyticsService.queryOptionBars(new RollingOptionSeriesRequest(
                normalizedUnderlying,
                expiryKind,
                expiryCode,
                strikeOffset,
                OptionType.fromCode(optionType),
                intervalMin,
                from,
                to,
                limit
        ));
        return ResponseEntity.ok(Map.of(
                "underlying", normalizedUnderlying,
                "expiryKind", expiryKind,
                "expiryCode", expiryCode,
                "strikeOffset", strikeOffset,
                "optionType", optionType,
                "intervalMin", intervalMin,
                "from", from,
                "to", to,
                "count", bars.size(),
                "bars", bars.stream().map(bar -> Map.of(
                        "timestampMs", bar.timestampMs(),
                        "openPaisa", bar.openPaisa(),
                        "highPaisa", bar.highPaisa(),
                        "lowPaisa", bar.lowPaisa(),
                        "closePaisa", bar.closePaisa(),
                        "volume", bar.volume(),
                        "iv", bar.iv(),
                        "oi", bar.oi(),
                        "spotPaisa", bar.spotPaisa(),
                        "strikePaisa", bar.strikePaisa()
                )).toList()
        ));
    }

    @PostMapping("/historical/equity/import-hive")
    ResponseEntity<Map<String, Object>> importEquityHive(@RequestBody(required = false) ImportHiveRequest request)
            throws Exception {
        ImportHiveRequest effective = request == null
                ? new ImportHiveRequest(null, null, null, null, null, null, null, null, null, null)
                : request;
        HiveCacheImportConfig config = toImportConfig(effective);
        if (effective.async()) {
            historicalDownloadExecutor.submit(() -> {
                try {
                    hiveCacheEquityImporter.importHive(config);
                } catch (Exception ex) {
                    throw new IllegalStateException("Hive import failed", ex);
                }
            });
            return ResponseEntity.accepted().body(Map.of("status", "RUNNING"));
        }
        HiveCacheImportResult result = hiveCacheEquityImporter.importHive(config);
        return ResponseEntity.ok(toImportResponse(result));
    }

    /** @deprecated Prefer {@code GET /api/v1/analytics/equity/candles}. */
    @Deprecated(forRemoval = false)
    @GetMapping("/historical/equity/candles")
    ResponseEntity<Map<String, Object>> equityCandles(
            @RequestParam String symbol,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(defaultValue = "5000") int limit
    ) throws Exception {
        InstrumentKey key = InstrumentKey.of(symbol, ExchangeSegment.NSE_EQ);
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDate fromDate = Instant.ofEpochMilli(from).atZone(ist).toLocalDate();
        LocalDate toDate = Instant.ofEpochMilli(to).atZone(ist).toLocalDate();
        List<Candle> candles = historicalAnalyticsService.queryEquityCandles(new CandleHistoryRequest(
                key,
                "1m",
                fromDate,
                toDate
        ));
        if (candles.size() > limit) {
            candles = candles.subList(0, limit);
        }
        List<Map<String, Object>> payload = candles.stream().map(this::toCandleMap).toList();
        return ResponseEntity.ok(Map.of(
                "symbol", key.symbol(),
                "from", from,
                "to", to,
                "count", payload.size(),
                "candles", payload
        ));
    }

    private Map<String, Object> toCandleMap(Candle candle) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("symbol", candle.symbol());
        row.put("interval", candle.interval());
        row.put("barTimeMs", candle.startTimeMs());
        row.put("openPaisa", candle.openPaisa());
        row.put("highPaisa", candle.highPaisa());
        row.put("lowPaisa", candle.lowPaisa());
        row.put("closePaisa", candle.closePaisa());
        row.put("volume", candle.volume());
        return row;
    }

    /** @deprecated Prefer {@code GET /api/v1/analytics/equity/universe}. */
    @Deprecated(forRemoval = false)
    @GetMapping("/historical/equity/universe")
    ResponseEntity<Map<String, Object>> equityUniverse() throws Exception {
        List<UniverseEntry> universe = historicalAnalyticsService.queryEquityUniverse();
        List<Map<String, Object>> rows = universe.stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("symbol", entry.symbol());
            row.put("companyName", entry.companyName());
            row.put("isin", entry.isin());
            row.put("industry", entry.industry());
            row.put("macroSector", entry.macroSector());
            row.put("asOfDate", entry.asOfDate());
            return row;
        }).toList();
        return ResponseEntity.ok(Map.of("count", rows.size(), "symbols", rows));
    }

    private Map<String, Object> toJobSummary(DownloadJobRecord job) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("jobId", job.jobId());
        summary.put("sourceType", job.sourceType().name());
        summary.put("status", job.status().name());
        summary.put("createdAtMs", job.createdAtMs());
        summary.put("startedAtMs", job.startedAtMs());
        summary.put("finishedAtMs", job.finishedAtMs());
        return summary;
    }

    private DownloadJobRecord findJob(String jobId) throws Exception {
        return downloadJobRegistry.findJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job " + jobId));
    }

    private DownloadJobStats jobStats(String jobId, DownloadJobRecord job) throws Exception {
        if (job.sourceType() == DownloadSourceType.EQUITY_INTRADAY) {
            return requireEquityService().stats(jobId);
        }
        return downloadJobService.stats(jobId);
    }

    private DownloadJobStats resumeJobInternal(String jobId, DownloadJobRecord job) throws Exception {
        if (job.sourceType() == DownloadSourceType.EQUITY_INTRADAY) {
            return requireEquityService().resumeJob(jobId);
        }
        return downloadJobService.resumeJob(jobId);
    }

    private EquityDownloadJobService requireEquityService() {
        EquityDownloadJobService service = equityDownloadJobService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Equity download requires Upstox market data provider");
        }
        return service;
    }

    private RollingOptionDownloadConfig toConfig(RollingOptionJobRequest request) {
        List<String> symbols = request.symbols() == null || request.symbols().isBlank()
                ? List.of("NIFTY", "BANKNIFTY")
                : List.of(request.symbols().split(","));
        List<Integer> intervals = request.intervals() == null || request.intervals().isBlank()
                ? List.of(5)
                : java.util.Arrays.stream(request.intervals().split(",")).map(String::trim).map(Integer::parseInt).toList();
        List<RollingExpiryRoll> expiries = request.expiries() == null || request.expiries().isBlank()
                ? List.of(
                        new RollingExpiryRoll(com.tradej.core.domain.instrument.RollingExpiryKind.WEEK, 1),
                        new RollingExpiryRoll(com.tradej.core.domain.instrument.RollingExpiryKind.WEEK, 2),
                        new RollingExpiryRoll(com.tradej.core.domain.instrument.RollingExpiryKind.MONTH, 1))
                : RollingExpiryRoll.parseList(List.of(request.expiries().split(",")));
        List<StrikeOffset> strikes = request.strikes() == null || request.strikes().isBlank()
                ? StrikeOffset.atmPlusMinus(10)
                : java.util.Arrays.stream(request.strikes().split(","))
                        .map(String::trim)
                        .map(StrikeOffset::parseSpec)
                        .toList();
        List<OptionType> optionTypes = request.optionTypes() == null || request.optionTypes().isBlank()
                ? List.of(OptionType.CALL, OptionType.PUT)
                : java.util.Arrays.stream(request.optionTypes().split(","))
                        .map(String::trim)
                        .map(OptionType::fromCode)
                        .toList();
        LocalDate from = request.from() == null ? LocalDate.of(2021, 1, 1) : request.from();
        LocalDate to = request.to() == null ? LocalDate.now() : request.to();
        ExchangeSegment segment = request.exchangeSegment() == null
                ? ExchangeSegment.IDX_I
                : ExchangeSegment.valueOf(request.exchangeSegment());
        return new RollingOptionDownloadConfig(
                symbols.stream().map(String::trim).map(INSTRUMENT_IDENTITY::canonicalSymbol).toList(),
                segment,
                from,
                to,
                intervals,
                expiries,
                strikes,
                optionTypes,
                request.delayMs() == null ? 350L : request.delayMs(),
                request.resume() != null && request.resume()
        );
    }

    private EquityHistoricalDownloadConfig toEquityConfig(EquityJobRequest request) {
        List<String> symbols;
        if (request.universe() != null && request.universe().equalsIgnoreCase("nifty500")) {
            symbols = List.of("NIFTY500");
        } else if (request.symbols() == null || request.symbols().isBlank()) {
            symbols = List.of("NIFTY500");
        } else {
            symbols = java.util.Arrays.stream(request.symbols().split(","))
                    .map(String::trim)
                    .map(INSTRUMENT_IDENTITY::canonicalSymbol)
                    .toList();
        }
        LocalDate to = request.to() == null ? LocalDate.now() : request.to();
        LocalDate from = request.from() == null ? to.minusDays(89) : request.from();
        ExchangeSegment segment = request.exchangeSegment() == null
                ? ExchangeSegment.NSE_EQ
                : ExchangeSegment.valueOf(request.exchangeSegment());
        return new EquityHistoricalDownloadConfig(
                symbols,
                segment,
                from,
                to,
                request.interval() == null ? "1m" : request.interval(),
                "interval=1m",
                1,
                request.rootPath() == null ? "data/historical-equity" : request.rootPath(),
                request.delayMs() == null ? 200L : request.delayMs(),
                request.workers() == null ? 8 : request.workers(),
                request.refreshUniverse() == null || request.refreshUniverse(),
                request.resume() != null && request.resume()
        );
    }

    private HiveCacheImportConfig toImportConfig(ImportHiveRequest request) {
        TradingProperties.HistoricalEquityProperties equity = tradingProperties.historicalEquity();
        String sourceHive = firstNonBlank(request.sourceHive(), equity.sourceHivePath());
        String universeCsv = firstNonBlank(request.universeCsv(), equity.sourceUniverseCsv());
        String industryParquet = firstNonBlank(request.industryParquet(), equity.sourceIndustryParquet());
        if (sourceHive == null || universeCsv == null || industryParquet == null) {
            throw new IllegalArgumentException(
                    "sourceHive, universeCsv, and industryParquet must be provided in request or application config"
            );
        }
        String rootPath = request.rootPath() == null || request.rootPath().isBlank()
                ? equity.rootPath()
                : request.rootPath();
        String fromMonth = request.fromMonth() == null || request.fromMonth().isBlank()
                ? equity.importFromMonth()
                : request.fromMonth();
        String toMonth = request.toMonth() == null || request.toMonth().isBlank()
                ? equity.importToMonth()
                : request.toMonth();
        List<String> symbols = request.symbols() == null || request.symbols().isBlank()
                ? List.of()
                : java.util.Arrays.stream(request.symbols().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(INSTRUMENT_IDENTITY::canonicalSymbol)
                        .toList();
        return new HiveCacheImportConfig(
                Path.of(sourceHive),
                Path.of(rootPath),
                Path.of(universeCsv),
                Path.of(industryParquet),
                fromMonth,
                toMonth,
                request.force() != null && request.force(),
                symbols,
                request.skipUniverseImport() == null || !request.skipUniverseImport()
        );
    }

    private static Map<String, Object> toImportResponse(HiveCacheImportResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partitionsDiscovered", result.partitionsDiscovered());
        body.put("symbolsProcessed", result.symbolsProcessed());
        body.put("filesWritten", result.filesWritten());
        body.put("filesSkipped", result.filesSkipped());
        body.put("filesMissingSource", result.filesMissingSource());
        body.put("filesFailed", result.filesFailed());
        body.put("totalRowsWritten", result.totalRowsWritten());
        body.put("elapsedMs", result.elapsedMs());
        body.put("failedDetails", result.failedDetails());
        body.put("missingMonthsBySymbol", result.missingMonthsBySymbol());
        return body;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    public record ImportHiveRequest(
            String sourceHive,
            String universeCsv,
            String industryParquet,
            String rootPath,
            String fromMonth,
            String toMonth,
            Boolean force,
            String symbols,
            Boolean skipUniverseImport,
            Boolean runAsync
    ) {
        boolean async() {
            return runAsync == null || runAsync;
        }
    }

    public record RollingOptionJobRequest(
            String symbols,
            String exchangeSegment,
            LocalDate from,
            LocalDate to,
            String intervals,
            String expiries,
            String strikes,
            String optionTypes,
            Long delayMs,
            Boolean resume,
            Boolean runAsync
    ) {
        long estimatedTasks(RollingOptionDownloadConfig config) {
            return com.tradej.historical.ingest.planner.RollingOptionDownloadPlanner.estimatedTaskCount(config);
        }

        boolean async() {
            return runAsync == null || runAsync;
        }
    }

    public record EquityJobRequest(
            String universe,
            String symbols,
            String exchangeSegment,
            LocalDate from,
            LocalDate to,
            String interval,
            String rootPath,
            Long delayMs,
            Integer workers,
            Boolean refreshUniverse,
            Boolean resume,
            Boolean runAsync
    ) {
        boolean async() {
            return runAsync == null || runAsync;
        }
    }
}
