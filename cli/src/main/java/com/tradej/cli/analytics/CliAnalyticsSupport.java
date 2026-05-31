package com.tradej.cli.analytics;

import com.tradej.analytics.catalog.HistoricalDataCatalog;
import com.tradej.analytics.config.DuckDbAnalyticsConfig;
import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.analytics.repository.DuckDbRollingOptionHistoricalRepository;
import com.tradej.analytics.repository.FederatedHistoricalBarRepository;
import com.tradej.analytics.service.DefaultHistoricalAnalyticsService;
import com.tradej.core.domain.model.AnalyticsCatalogSnapshot;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.infrastructure.WorkspacePaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public final class CliAnalyticsSupport {

    private CliAnalyticsSupport() {
    }

    public static DefaultHistoricalAnalyticsService openService(
            String equityRoot,
            String optionsWarehouse,
            String runtimeDb,
            boolean attachRuntimeDb
    ) {
        WorkspacePaths workspace = WorkspacePaths.fromSystemProperty();
        Path equity = workspace.historicalEquityRoot(equityRoot);
        Path options = workspace.resolve(optionsWarehouse);
        Path runtime = runtimeDb == null || runtimeDb.isBlank()
                ? workspace.resolve("runtime-dev/trade.duckdb")
                : workspace.resolve(runtimeDb);
        DuckDbAnalyticsEngine engine = new DuckDbAnalyticsEngine(new DuckDbAnalyticsConfig(
                equity,
                options,
                runtime,
                attachRuntimeDb,
                true,
                DuckDbAnalyticsConfig.DEFAULT_SQL_MAX_ROWS,
                DuckDbAnalyticsConfig.DEFAULT_SQL_MAX_RUNTIME_MS
        ));
        HistoricalDataCatalog catalog = new HistoricalDataCatalog(engine);
        FederatedHistoricalBarRepository equityRepository = new FederatedHistoricalBarRepository(engine);
        DuckDbRollingOptionHistoricalRepository optionRepository = new DuckDbRollingOptionHistoricalRepository(engine);
        return new DefaultHistoricalAnalyticsService(
                equityRepository,
                optionRepository,
                catalog,
                engine
        );
    }

    public static boolean warehousePresent(String equityRoot) {
        Path root = WorkspacePaths.fromSystemProperty().historicalEquityRoot(equityRoot);
        return Files.isDirectory(root.resolve("bars").resolve("interval=1m"));
    }

    public static AnalyticsCatalogSnapshot catalog(DefaultHistoricalAnalyticsService service) {
        return service.catalog();
    }

    public static AnalyticsQueryResult sql(DefaultHistoricalAnalyticsService service, String query, int limit) {
        return service.executeReadOnlySql(query, limit);
    }

    public static java.util.List<com.tradej.core.domain.model.Candle> equityCandles(
            DefaultHistoricalAnalyticsService service,
            String symbol,
            String interval,
            LocalDate from,
            LocalDate to
    ) {
        return service.queryEquityCandles(new CandleHistoryRequest(
                InstrumentKey.of(symbol, ExchangeSegment.NSE_EQ),
                interval,
                from,
                to
        ));
    }

    public static java.util.List<com.tradej.core.domain.model.RollingOptionBar> optionBars(
            DefaultHistoricalAnalyticsService service,
            String underlying,
            String expiryKind,
            int expiryCode,
            int strikeOffset,
            String optionType,
            int intervalMin,
            long fromMs,
            long toMs,
            int limit
    ) {
        return service.queryOptionBars(new RollingOptionSeriesRequest(
                underlying,
                expiryKind,
                expiryCode,
                strikeOffset,
                OptionType.fromCode(optionType),
                intervalMin,
                fromMs,
                toMs,
                limit
        ));
    }
}
