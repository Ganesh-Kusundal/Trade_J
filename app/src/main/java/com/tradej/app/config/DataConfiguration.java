package com.tradej.app.config;

import com.tradej.analytics.catalog.HistoricalDataCatalog;
import com.tradej.analytics.config.DuckDbAnalyticsConfig;
import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.analytics.pipeline.AnalyticsPipelineSharedState;
import com.tradej.analytics.repository.DuckDbRollingOptionHistoricalRepository;
import com.tradej.analytics.repository.FederatedHistoricalBarRepository;
import com.tradej.analytics.service.DefaultHistoricalAnalyticsService;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.core.depth.EventBusDepthBridge;
import com.tradej.broker.core.depth.OrderBookEngine;
import com.tradej.broker.dhan.depth.DhanMarketDepthProvider;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.core.domain.port.HistoricalAnalyticsService;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.infrastructure.WorkspacePaths;
import com.tradej.execution.identity.OrderIdentityRehydrator;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.readmodel.ReadModelStore;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.feature.store.AsyncDuckDbWriter;
import com.tradej.feature.store.DuckDbFeatureStore;
import com.tradej.feature.store.InMemoryFeatureStore;
import com.tradej.feature.store.OptionsAwareFeatureStore;
import com.tradej.gateway.bridge.GatewayEventBridge;
import com.tradej.historical.ingest.importing.HiveCacheEquityImporter;
import com.tradej.historical.ingest.service.DownloadJobRegistry;
import com.tradej.historical.ingest.service.DownloadJobService;
import com.tradej.historical.ingest.service.EquityDownloadJobService;
import com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse;
import com.tradej.options.calculator.MaxPainCalculator;
import com.tradej.options.greeks.OptionsAnalyticsCache;
import com.tradej.options.surface.VolatilitySurfaceBuilder;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.chronicle.ChronicleDeadLetterQueue;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import com.tradej.persistence.replay.HistoricalRangeService;
import com.tradej.persistence.replay.ReplayRunner;
import com.tradej.persistence.replay.ReplayStateManager;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.replay.engine.BacktestExecutionService;
import com.tradej.replay.engine.IsolatedReplayStateManager;
import com.tradej.replay.engine.PositionStateRebuilder;
import com.tradej.replay.engine.ReplayOrchestrator;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unified data configuration consolidating persistence, feature stores,
 * analytics, historical downloads, depth analytics, and read models.
 */
@Configuration
@EnableScheduling
public class DataConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataConfiguration.class);

    // ── Workspace ──

    @Bean
    WorkspacePaths workspacePaths() {
        return WorkspacePaths.fromSystemProperty();
    }

    @Bean
    Path workspaceRoot(WorkspacePaths workspacePaths) {
        return workspacePaths.workspaceRoot();
    }

    // ── Read model ──

    @Bean
    ReadModelStore readModelStore() {
        return new ReadModelStore();
    }

    // ── Persistence ──

    private static final String OMS_QUEUE_SUBDIR = "oms";
    private static final String DLQ_QUEUE_SUBDIR = "dlq";

    @Bean(destroyMethod = "close")
    DeadLetterQueue deadLetterQueue(TradingProperties properties) {
        Path dlqPath = Path.of(properties.storage().chroniclePath()).resolve(DLQ_QUEUE_SUBDIR);
        return new ChronicleDeadLetterQueue(dlqPath);
    }

    /**
     * Separate inner config for the @Scheduled retention cleanup — Spring
     * requires @Scheduled methods to be parameterless, so dependencies are
     * injected via constructor.
     */
    @Configuration
    static class ChronicleRetentionConfig {

        private static final Logger log = LoggerFactory.getLogger(ChronicleRetentionConfig.class);

        private final ChronicleAuditLogWriter auditLogWriter;
        private final DeadLetterQueue dlq;

        ChronicleRetentionConfig(
                ChronicleAuditLogWriter auditLogWriter,
                @org.springframework.beans.factory.annotation.Qualifier("deadLetterQueue") DeadLetterQueue dlq
        ) {
            this.auditLogWriter = auditLogWriter;
            this.dlq = dlq;
        }

        @Scheduled(cron = "${tradej.chronicle.retention-cron:0 0 3 * * *}")
        void chronicleRetentionCleanup() {
            long retentionDays = 30; // default 30-day retention
            try {
                String configuredDays = System.getenv("CHRONICLE_RETENTION_DAYS");
                if (configuredDays != null && !configuredDays.isBlank()) {
                    retentionDays = Long.parseLong(configuredDays);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid CHRONICLE_RETENTION_DAYS value, using default {} days", retentionDays);
            }
            int auditDeleted = auditLogWriter.cleanupOldFiles(retentionDays);
            if (dlq instanceof ChronicleDeadLetterQueue chronicleDlq) {
                int dlqDeleted = chronicleDlq.cleanupOldFiles(retentionDays);
                if (auditDeleted > 0 || dlqDeleted > 0) {
                    log.info("Chronicle retention cleanup: audit={} dlq={} files deleted (retention={} days)",
                            auditDeleted, dlqDeleted, retentionDays);
                }
            }
        }
    }

    @Bean
    ChronicleAuditLogWriter chronicleAuditLogWriter(TradingProperties properties) {
        return new ChronicleAuditLogWriter(Path.of(properties.storage().chroniclePath()));
    }

    @Bean
    DuckDbEventStore duckDbEventStore(TradingProperties properties) {
        return new DuckDbEventStore(Path.of(properties.storage().duckdbPath()));
    }

    @Bean(destroyMethod = "close")
    AsyncDuckDbEventStore asyncDuckDbEventStore(DuckDbEventStore duckDbEventStore) {
        AsyncDuckDbEventStore store = new AsyncDuckDbEventStore(duckDbEventStore);
        store.start();
        return store;
    }

    @Bean
    EventSourcedOrderRepository eventSourcedOrderRepository(TradingProperties properties) {
        Path chronicleBase = Path.of(properties.storage().chroniclePath());
        Path omsQueuePath = chronicleBase.resolve(OMS_QUEUE_SUBDIR);
        return new EventSourcedOrderRepository(omsQueuePath);
    }

    @Bean(name = "localHistoricalRangeService")
    HistoricalRangeService localHistoricalRangeService(TradingProperties properties) {
        return new HistoricalRangeService(Path.of(properties.storage().duckdbPath()));
    }

    @Bean
    DuckDbPipelineGraphStore duckDbPipelineGraphStore(TradingProperties properties) {
        return new DuckDbPipelineGraphStore(Path.of(properties.storage().duckdbPath()));
    }

    @Bean(destroyMethod = "close")
    ReplayRunner replayRunner(TradingProperties properties, EventBus eventBus, VirtualClock virtualClock, ReplayStateManager replayStateManager) {
        return new ReplayRunner(Path.of(properties.storage().chroniclePath()), eventBus, virtualClock, replayStateManager);
    }

    @Bean
    ReplayStateManager replayStateManager(
            PortfolioEngine portfolioEngine,
            EventSourcedNetPositionProvider netPositionProvider,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            ReadModelStore readModelStore,
            OrderManagementService orderManagementService
    ) {
        return new IsolatedReplayStateManager(
                portfolioEngine,
                netPositionProvider,
                positionRiskHandler,
                candleAggregationService,
                readModelStore,
                orderManagementService
        );
    }

    @Bean
    @Profile("replay")
    VirtualClock replayClock(EventBus eventBus) {
        return new VirtualClock(VirtualClock.Mode.REPLAY, eventBus);
    }

    @Bean
    ApplicationRunner orderIdentityRehydrationRunner(
            EventSourcedOrderRepository omsRepo,
            OrderIdentityRegistry identityRegistry
    ) {
        return args -> OrderIdentityRehydrator.rehydrate(omsRepo, identityRegistry);
    }

    @Bean
    ReplayOrchestrator replayOrchestrator(
            ReplayRunner replayRunner,
            HistoricalRangeService localHistoricalRangeService,
            VirtualClock virtualClock,
            ReplayStateManager replayStateManager
    ) {
        return new ReplayOrchestrator(replayRunner, localHistoricalRangeService, virtualClock, replayStateManager);
    }

    @Bean
    PositionStateRebuilder positionStateRebuilder(ReplayOrchestrator replayOrchestrator) {
        return new PositionStateRebuilder(replayOrchestrator);
    }

    @Bean
    com.tradej.replay.engine.ScenarioRunner scenarioRunner(
            com.tradej.core.domain.port.EventBus eventBus,
            com.tradej.replay.engine.CandleReplaySession candleSession,
            com.tradej.replay.engine.BacktestExecutionService backtestService,
            com.tradej.core.domain.port.HistoricalBarRepository barRepository,
            org.springframework.beans.factory.ObjectProvider<com.tradej.persistence.replay.HistoricalQueryService> queryServiceProvider,
            org.springframework.beans.factory.ObjectProvider<com.tradej.persistence.replay.HistoricalEventReplayService> eventReplayServiceProvider
    ) {
        // The ScenarioRunner is the single entry point for replay,
        // backtest, and scanner-on-replay. The strategyHash is empty
        // and the seed is 42L (deterministic). A production
        // deployment would parameterize these.
        //
        // HistoricalQueryService and HistoricalEventReplayService
        // are not yet Spring beans in dev mode (they require a
        // DuckDB Connection); the ObjectProvider lets us wire the
        // runner anyway. The corresponding Kinds return a graceful
        // error if their service is absent.
        com.tradej.persistence.replay.HistoricalQueryService qs = queryServiceProvider.getIfAvailable();
        com.tradej.persistence.replay.HistoricalEventReplayService ers = eventReplayServiceProvider.getIfAvailable();
        return new com.tradej.replay.engine.ScenarioRunner(
                eventBus, candleSession, backtestService, barRepository,
                qs, ers, "", 42L);
    }

    @Bean
    com.tradej.replay.engine.AdminReplayAdapter adminReplayAdapter(
            com.tradej.replay.engine.ScenarioRunner scenarioRunner
    ) {
        // Bridge from the admin path inputs (symbol, from, to,
        // offset, batchSize) to the unified ScenarioRunner. The
        // /admin/historical/replay/ticks path is the first to
        // route through this adapter; the other three paths still
        // route through the orchestrator until their Kinds
        // (REPLAY_CANDLES via candle session facade, REPLAY_EVENTS
        // for projection-store path) are added in follow-up
        // commits.
        return new com.tradej.replay.engine.AdminReplayAdapter(scenarioRunner);
    }

    // ── Feature store ──

    private static final String FEATURES_DB = "-features.duckdb";

    @Bean
    OptionsAwareFeatureStore hotPathFeatureStore() {
        return new OptionsAwareFeatureStore(new InMemoryFeatureStore());
    }

    @Bean
    @Primary
    FeatureStore featureStore(OptionsAwareFeatureStore hotPathFeatureStore) {
        return hotPathFeatureStore;
    }

    @Bean(destroyMethod = "close")
    DuckDbFeatureStore duckDbFeatureStore(TradingProperties properties) {
        Path featuresPath = resolveFeaturesPath(properties);
        return new DuckDbFeatureStore(featuresPath);
    }

    @Bean(destroyMethod = "close")
    AsyncDuckDbWriter asyncDuckDbWriter(DuckDbFeatureStore duckDbFeatureStore) {
        AsyncDuckDbWriter writer = new AsyncDuckDbWriter(duckDbFeatureStore);
        writer.start();
        log.info("AsyncDuckDbWriter bean created and started");
        return writer;
    }

    static Path resolveFeaturesPath(TradingProperties properties) {
        Path basePath = Path.of(properties.storage().duckdbPath());
        String baseName = basePath.getFileName().toString();
        String featuresName = baseName.replaceAll("\\.duckdb$", "") + FEATURES_DB;
        return basePath.getParent() != null
                ? basePath.getParent().resolve(featuresName)
                : Path.of(featuresName);
    }

    // ── Analytics ──

    @Bean(destroyMethod = "close")
    DuckDbAnalyticsEngine duckDbAnalyticsEngine(TradingProperties properties, WorkspacePaths workspacePaths) {
        TradingProperties.AnalyticsProperties analytics = properties.analytics();
        Path equityRoot = workspacePaths.historicalEquityRoot(analytics.equityRoot());
        Path optionsWarehouse = Path.of(analytics.optionsWarehouse());
        if (!optionsWarehouse.isAbsolute()) {
            optionsWarehouse = workspacePaths.resolve(analytics.optionsWarehouse());
        }
        Path runtimeDb = analytics.runtimeDbPath() == null || analytics.runtimeDbPath().isBlank()
                ? workspacePaths.resolve("runtime-dev/trade.duckdb")
                : Path.of(analytics.runtimeDbPath());
        if (!runtimeDb.isAbsolute()) {
            runtimeDb = workspacePaths.resolve(analytics.runtimeDbPath());
        }
        return new DuckDbAnalyticsEngine(new DuckDbAnalyticsConfig(
                equityRoot,
                optionsWarehouse,
                runtimeDb,
                analytics.attachRuntimeDb(),
                analytics.sqlEnabled(),
                analytics.sqlMaxRows(),
                analytics.sqlMaxRuntimeMs()
        ));
    }

    @Bean
    HistoricalDataCatalog historicalDataCatalog(DuckDbAnalyticsEngine engine) {
        return new HistoricalDataCatalog(engine);
    }

    @Bean
    HistoricalBarRepository historicalBarRepository(DuckDbAnalyticsEngine engine) {
        return new FederatedHistoricalBarRepository(engine);
    }

    @Bean
    RollingOptionHistoricalRepository rollingOptionHistoricalRepository(DuckDbAnalyticsEngine engine) {
        return new DuckDbRollingOptionHistoricalRepository(engine);
    }

    @Bean
    HistoricalAnalyticsService historicalAnalyticsService(
            HistoricalBarRepository historicalBarRepository,
            RollingOptionHistoricalRepository rollingOptionHistoricalRepository,
            HistoricalDataCatalog historicalDataCatalog,
            DuckDbAnalyticsEngine duckDbAnalyticsEngine
    ) {
        return new DefaultHistoricalAnalyticsService(
                historicalBarRepository,
                rollingOptionHistoricalRepository,
                historicalDataCatalog,
                duckDbAnalyticsEngine
        );
    }

    @Bean
    Map<String, Object> analyticsPipelineSharedState(
            HistoricalBarRepository historicalBarRepository,
            RollingOptionHistoricalRepository rollingOptionHistoricalRepository
    ) {
        return AnalyticsPipelineSharedState.create(
                historicalBarRepository,
                rollingOptionHistoricalRepository
        );
    }

    // ── Options analytics (conditional) ──


    @Bean
    @org.springframework.context.annotation.Lazy
    BacktestExecutionService backtestExecutionService(
            com.tradej.pipeline.service.DagPipelineRuntimeService dagPipelineRuntimeService,
            com.tradej.persistence.replay.ReplayStateManager replayStateManager
    ) {
        return new BacktestExecutionService(dagPipelineRuntimeService, replayStateManager);
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "trade.options.analytics-enabled", havingValue = "true", matchIfMissing = false)
    static class OptionsAnalyticsConfig {

        private static final Logger log = LoggerFactory.getLogger(OptionsAnalyticsConfig.class);

        @Bean
        OptionsAnalyticsCache optionsAnalyticsCache() {
            return new OptionsAnalyticsCache();
        }

        @Bean
        VolatilitySurfaceBuilder volatilitySurfaceBuilder(OptionsAnalyticsCache cache) {
            return new VolatilitySurfaceBuilder(cache);
        }

        @Bean
        OptionChainPollingService optionChainPollingService(
                IBrokerConnection brokerConnection,
                EventBus eventBus,
                VolatilitySurfaceBuilder surfaceBuilder
        ) {
            return new OptionChainPollingService(brokerConnection, eventBus, surfaceBuilder);
        }

        public static final class OptionChainPollingService {

            private final IBrokerConnection brokerConnection;
            private final EventBus eventBus;
            private final VolatilitySurfaceBuilder surfaceBuilder;

            public OptionChainPollingService(
                    IBrokerConnection brokerConnection,
                    EventBus eventBus,
                    VolatilitySurfaceBuilder surfaceBuilder
            ) {
                this.brokerConnection = brokerConnection;
                this.eventBus = eventBus;
                this.surfaceBuilder = surfaceBuilder;
            }

            @Scheduled(fixedDelayString = "${trade.options.chain-poll-interval-ms:60000}")
            public void pollNiftyChain() {
                brokerConnection.getCapability(OptionsProvider.class).ifPresent(provider -> {
                    try {
                        List<LocalDate> expiries = provider.getExpiries("NIFTY", ExchangeSegment.NSE_FNO);
                        if (expiries.isEmpty()) {
                            return;
                        }
                        LocalDate expiry = expiries.getFirst();
                        OptionChainSnapshot chain = provider.getOptionChain("NIFTY", ExchangeSegment.NSE_FNO, expiry);
                        eventBus.publish(new OptionChainUpdated(EventMetadata.root(), chain));
                        surfaceBuilder.build(chain);
                        MaxPainCalculator.MaxPainResult maxPain = MaxPainCalculator.compute(chain);
                        eventBus.publish(new MaxPainComputed(
                                EventMetadata.root(),
                                "NIFTY",
                                expiry,
                                maxPain.strikePaisa(),
                                maxPain.totalPainPaisa()));
                    } catch (Exception e) {
                        log.warn("Option chain poll failed: {}", e.getMessage());
                    }
                });
            }
        }
    }

    // ── Historical download ──

    @Bean(destroyMethod = "close")
    DuckDbHistoricalWarehouse duckDbHistoricalWarehouse(TradingProperties properties) {
        return new DuckDbHistoricalWarehouse(Path.of(properties.storage().historicalWarehousePath()));
    }

    @Bean
    DownloadJobService downloadJobService(
            DuckDbHistoricalWarehouse warehouse,
            OptionsProvider optionsProvider,
            TradingProperties properties
    ) {
        long delayMs = properties.download().delayMs();
        int workers = properties.download().workers();
        return new DownloadJobService(
                warehouse,
                optionsProvider,
                workers,
                System::currentTimeMillis,
                () -> sleep(delayMs)
        );
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnBean(MarketDataProvider.class)
    EquityDownloadJobService equityDownloadJobService(
            MarketDataProvider marketDataProvider,
            InstrumentResolver instrumentResolver,
            TradingProperties properties,
            WorkspacePaths workspacePaths,
            com.tradej.historical.ingest.canonical.ParquetWriteService parquetWriteService
    ) {
        TradingProperties.HistoricalEquityProperties equity = properties.historicalEquity();
        return new EquityDownloadJobService(
                workspacePaths.historicalEquityRoot(equity.rootPath()),
                marketDataProvider,
                instrumentResolver,
                equity.workers(),
                System::currentTimeMillis,
                () -> sleep(equity.delayMs()),
                equity.universeUrl(),
                parquetWriteService
        );
    }

    @Bean
    HiveCacheEquityImporter hiveCacheEquityImporter() {
        return new HiveCacheEquityImporter();
    }

    @Bean(destroyMethod = "close")
    DownloadJobRegistry downloadJobRegistry(TradingProperties properties, WorkspacePaths workspacePaths) {
        Path optionsWarehouse = workspacePaths.resolve(properties.storage().historicalWarehousePath());
        Path equityRoot = workspacePaths.historicalEquityRoot(properties.historicalEquity().rootPath());
        return new DownloadJobRegistry(optionsWarehouse, equityRoot);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService historicalDownloadExecutor(TradingProperties properties) {
        int workers = Math.max(1, properties.historicalEquity().workers());
        return Executors.newFixedThreadPool(workers, r -> {
            Thread thread = new Thread(r, "historical-download");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    com.tradej.historical.ingest.calendar.TradingCalendarStore tradingCalendarStore() {
        return new com.tradej.historical.ingest.calendar.TradingCalendarStore();
    }

    @Bean
    com.tradej.historical.ingest.calendar.CompositeHolidayCalendar compositeHolidayCalendar(
            com.tradej.historical.ingest.calendar.TradingCalendarStore tradingCalendarStore,
            @org.springframework.beans.factory.annotation.Qualifier("canonicalDataRoot") Path dataRoot) {
        var calendar = new com.tradej.historical.ingest.calendar.CompositeHolidayCalendar(tradingCalendarStore);
        calendar.refreshFromData(dataRoot);
        return calendar;
    }

    @Bean
    com.tradej.historical.ingest.sync.DataGapScanService dataGapScanService(
            com.tradej.historical.ingest.calendar.CompositeHolidayCalendar calendar,
            @org.springframework.beans.factory.annotation.Qualifier("canonicalDataRoot") Path dataRoot) {
        return new com.tradej.historical.ingest.sync.DataGapScanService(calendar, dataRoot);
    }

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("canonicalDataRoot")
    Path canonicalDataRoot(TradingProperties properties, WorkspacePaths workspacePaths) {
        return workspacePaths.historicalEquityRoot(
                properties.historicalEquity().rootPath()).getParent();
    }

    @Bean
    com.tradej.historical.ingest.sync.GapDetector gapDetector(
            com.tradej.historical.ingest.calendar.TradingCalendarStore calendar) {
        return new com.tradej.historical.ingest.sync.GapDetector(calendar);
    }

    @Bean
    com.tradej.historical.ingest.canonical.ParquetWriteService parquetWriteService(
            @org.springframework.beans.factory.annotation.Qualifier("canonicalDataRoot") Path dataRoot) {
        return new com.tradej.historical.ingest.canonical.CanonicalBarWriter(dataRoot);
    }

    @Bean
    com.tradej.historical.ingest.sync.RuntimeParquetExporter runtimeParquetExporter(
            TradingProperties properties,
            com.tradej.historical.ingest.canonical.ParquetWriteService parquetWriteService) {
        Path runtimeDb = Path.of(properties.storage().duckdbPath());
        return new com.tradej.historical.ingest.sync.RuntimeParquetExporter(
                runtimeDb, parquetWriteService, "NSE_EQ");
    }

    @Bean
    com.tradej.app.sync.SyncStatusStore syncStatusStore(TradingProperties properties) {
        return new com.tradej.app.sync.SyncStatusStore(Path.of(properties.storage().duckdbPath()));
    }

    @Bean
    com.tradej.historical.ingest.canonical.CanonicalBarQuery canonicalBarQuery(
            @org.springframework.beans.factory.annotation.Qualifier("canonicalDataRoot") Path dataRoot) {
        return new com.tradej.historical.ingest.canonical.CanonicalBarQuery(
                com.tradej.historical.ingest.canonical.CanonicalPaths.barsRoot(dataRoot));
    }

    @Bean
    com.tradej.historical.ingest.canonical.MultiIntervalGenerator multiIntervalGenerator(
            com.tradej.historical.ingest.canonical.HistoricalDataStore dataStore,
            com.tradej.historical.ingest.canonical.ParquetWriteService writer) {
        return new com.tradej.historical.ingest.canonical.MultiIntervalGenerator(
                (com.tradej.historical.ingest.canonical.ParquetHistoricalDataStore) dataStore, writer);
    }

    @Bean
    com.tradej.historical.ingest.canonical.HistoricalDataStore historicalDataStore(
            TradingProperties properties,
            WorkspacePaths workspacePaths,
            com.tradej.historical.ingest.calendar.TradingCalendarStore calendar) {
        Path dataRoot = workspacePaths.historicalEquityRoot(
                properties.historicalEquity().rootPath()).getParent();
        return new com.tradej.historical.ingest.canonical.ParquetHistoricalDataStore(
                dataRoot, calendar);
    }

    @Bean
    com.tradej.historical.ingest.replay.ParquetReplayAdapter parquetReplayAdapter(
            com.tradej.historical.ingest.canonical.HistoricalDataStore dataStore) {
        return new com.tradej.historical.ingest.replay.ParquetReplayAdapter(dataStore);
    }

    @Bean
    @ConditionalOnBean(MarketDataProvider.class)
    com.tradej.historical.ingest.sync.IncrementalSyncService incrementalSyncService(
            MarketDataProvider marketDataProvider,
            InstrumentResolver instrumentResolver,
            com.tradej.historical.ingest.calendar.TradingCalendarStore calendar,
            com.tradej.historical.ingest.canonical.ParquetWriteService parquetWriteService) {
        return new com.tradej.historical.ingest.sync.IncrementalSyncService(
                marketDataProvider, instrumentResolver, calendar, parquetWriteService);
    }

    @Bean
    @ConditionalOnBean(MarketDataProvider.class)
    com.tradej.historical.ingest.sync.BackfillService backfillService(
            com.tradej.historical.ingest.sync.GapDetector gapDetector,
            MarketDataProvider marketDataProvider,
            com.tradej.historical.ingest.canonical.ParquetWriteService parquetWriteService) {
        return new com.tradej.historical.ingest.sync.BackfillService(
                gapDetector, marketDataProvider, parquetWriteService);
    }

    // ── Depth analytics (order book) ──

    @Configuration
    static class DepthAnalyticsConfig {

        private EventBusDepthBridge depthBridge;

        @Bean
        public OrderBookEngine orderBookEngine() {
            return new OrderBookEngine();
        }

        @Bean
        public EventBusDepthBridge eventBusDepthBridge(OrderBookEngine orderBookEngine, EventBus eventBus) {
            this.depthBridge = new EventBusDepthBridge(orderBookEngine, eventBus);
            return this.depthBridge;
        }

        @PostConstruct
        void wireDepthBridge() {
            if (depthBridge != null) {
                depthBridge.start();
            }
        }

        @PreDestroy
        void unwireDepthBridge() {
            if (depthBridge != null) {
                depthBridge.stop();
            }
        }

        @Bean
        public com.tradej.execution.depth.OrderBookImbalanceService orderBookImbalanceService() {
            return new com.tradej.execution.depth.OrderBookImbalanceService();
        }

        @Bean
        public com.tradej.execution.depth.HeatmapRecorder heatmapRecorder() {
            return new com.tradej.execution.depth.HeatmapRecorder();
        }

        @Bean
        public com.tradej.execution.depth.RestingOrderAnalyzer restingOrderAnalyzer() {
            return new com.tradej.execution.depth.RestingOrderAnalyzer();
        }

        @Bean
        public com.tradej.execution.depth.IcebergDetector icebergDetector() {
            return new com.tradej.execution.depth.IcebergDetector();
        }

        @Bean
        public com.tradej.execution.depth.AbsorptionAnalyzer absorptionAnalyzer() {
            return new com.tradej.execution.depth.AbsorptionAnalyzer();
        }

        @Bean
        public com.tradej.execution.depth.DepthAnalyticsPipeline depthAnalyticsPipeline(
                OrderBookEngine orderBookEngine,
                com.tradej.execution.depth.OrderBookImbalanceService imbalanceService,
                com.tradej.execution.depth.HeatmapRecorder heatmapRecorder,
                com.tradej.execution.depth.RestingOrderAnalyzer restingOrderAnalyzer,
                com.tradej.execution.depth.IcebergDetector icebergDetector,
                com.tradej.execution.depth.AbsorptionAnalyzer absorptionAnalyzer,
                ObjectProvider<GatewayEventBridge> bridgeProvider) {

            com.tradej.execution.depth.DepthAnalyticsPipeline pipeline = new com.tradej.execution.depth.DepthAnalyticsPipeline(
                    orderBookEngine, imbalanceService, heatmapRecorder,
                    restingOrderAnalyzer, icebergDetector, absorptionAnalyzer);

            bridgeProvider.ifAvailable(bridge -> pipeline.addConsumer(bridge::publishDepthAnalytics));
            orderBookEngine.addListener(pipeline::onDepthUpdate);

            return pipeline;
        }
    }

    // ── Dhan depth provider ──

    @Configuration
    static class DhanDepthConfig {

        private final DhanMarketDepthProvider provider;

        public DhanDepthConfig(OrderBookEngine orderBookEngine) {
            this.provider = new DhanMarketDepthProvider(orderBookEngine);
        }

        @Bean
        public DhanMarketDepthProvider dhanMarketDepthProvider() {
            return provider;
        }

        @Bean
        public com.tradej.broker.api.port.OrderBookSnapshotProvider dhanOrderBookSnapshotProvider() {
            return new com.tradej.broker.api.port.OrderBookSnapshotProvider() {
                @Override
                public Object snapshot(String symbol, com.tradej.core.domain.value.ExchangeSegment segment, int levels) {
                    return provider.snapshot(symbol, segment, levels);
                }

                @Override
                public java.util.List<Object> snapshotAll(int levels) {
                    return provider.snapshotsAll(levels).stream().map(s -> (Object) s).toList();
                }

                @Override
                public int bookCount() {
                    return provider.bookCount();
                }

                @Override
                public java.util.Map<String, com.tradej.core.domain.value.ExchangeSegment> activeBooks() {
                    return provider.activeBooks();
                }
            };
        }

        @PostConstruct
        void start() {
            provider.start();
        }

        @PreDestroy
        void stop() {
            provider.stop();
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
