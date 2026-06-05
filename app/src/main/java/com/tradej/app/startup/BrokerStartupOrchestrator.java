package com.tradej.app.startup;

import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.app.config.BrokerRuntimeMode;
import com.tradej.app.config.BrokerRuntimeModeResolver;
import com.tradej.app.config.ScanProperties;
import com.tradej.app.config.TradingProperties;
import com.tradej.app.health.BrokerErrorTracker;
import com.tradej.app.pipeline.DagPipelineIngressBridge;
import com.tradej.app.pipeline.PositionStateRebuilder;
import com.tradej.app.readmodel.ReadModelStore;
import com.tradej.app.scanner.RuntimeSubscriptionManager;
import com.tradej.app.subscription.SubscriptionCoordinator;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanBrokerStartup;
import com.tradej.broker.icici.IciciBrokerConnection;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.broker.upstox.http.UpstoxApiException;
import com.tradej.core.domain.event.BrokerAdapterError;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.EventBus;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.feature.store.AsyncDuckDbWriter;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public final class BrokerStartupOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BrokerStartupOrchestrator.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final BrokerRuntimeModeResolver runtimeModeResolver;

    public BrokerStartupOrchestrator(BrokerRuntimeModeResolver runtimeModeResolver) {
        this.runtimeModeResolver = runtimeModeResolver;
    }

    public void runStartup(
            TradingProperties properties,
            ObjectProvider<ScanProperties> scanPropertiesProvider,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
            ObjectProvider<DhanTokenProvider> dhanTokenProvider,
            ObjectProvider<BreezeTokenProvider> breezeTokenProvider,
            RuntimeHealthState runtimeHealthState,
            EventBus eventBus,
            MarketDataPipeline marketDataPipeline,
            OrderPipeline orderPipeline,
            AsyncDuckDbWriter asyncDuckDbWriter,
            ChronicleAuditLogWriter chronicleAuditLogWriter,
            AsyncDuckDbEventStore asyncDuckDbEventStore,
            ReconciliationAlertLogger reconciliationAlertLogger,
            BrokerErrorTracker brokerErrorTracker,
            ReadModelStore readModelStore,
            EventSourcedNetPositionProvider netPositionProvider,
            ObjectProvider<RuntimeSubscriptionManager> subscriptionManagerProvider,
            ObjectProvider<SubscriptionCoordinator> subscriptionCoordinatorProvider,
            DagPipelineIngressBridge dagPipelineIngressBridge,
            PositionStateRebuilder positionStateRebuilder,
            OrderManagementService orderManagementService
    ) {
        BrokerRuntimeMode mode = runtimeModeResolver.resolve();

        loadCatalog(properties, brokerConnection, runtimeHealthState, mode);
        boolean scanEnabled = scanPropertiesProvider.getIfAvailable() != null
                && scanPropertiesProvider.getIfAvailable().enabled();
        List<MarketSubscriptionRequest> subscriptions = validateSubscriptions(
                properties, brokerConnection, brokerCapabilities, scanEnabled, mode);

        if (mode != BrokerRuntimeMode.UPSTOX_ANALYTICS_REST) {
            dhanTokenProvider.ifAvailable(DhanTokenProvider::ensureValid);
            breezeTokenProvider.ifAvailable(BreezeTokenProvider::ensureValid);
        }

        if (!subscriptions.isEmpty()) {
            DhanApiEnvironment environment = properties.broker() != null
                    ? properties.broker().environment()
                    : DhanApiEnvironment.LIVE;
            verifyBrokerPreflight(brokerConnection, subscriptions, environment, mode);
        } else if (mode.isAnalyticsRest()) {
            verifyAnalyticsRestPreflight(brokerConnection, properties);
        }
        runtimeHealthState.markBrokerPreflightPassed();

        subscribeEventHandlers(eventBus, asyncDuckDbWriter, chronicleAuditLogWriter,
                asyncDuckDbEventStore, brokerErrorTracker, readModelStore,
                netPositionProvider, reconciliationAlertLogger, dagPipelineIngressBridge);
        if (mode.expectsWebSocket()) {
            setupWebSocketHandlers(brokerConnection, marketDataPipeline, orderPipeline, eventBus);
        } else {
            log.info(
                    "Skipping WebSocket handler wiring in {} mode (REST-only transport).",
                    mode);
        }

        eventBus.start();
        orderManagementService.replayAll();
        positionStateRebuilder.rebuild(eventBus);

        if (!mode.expectsWebSocket()) {
            log.warn(
                    "Skipping broker WebSocket connect in {} mode. REST APIs remain available.",
                    mode);
        } else {
            brokerConnection.connect();
            RuntimeSubscriptionManager subscriptionManager = subscriptionManagerProvider.getIfAvailable();
            if (subscriptionManager != null) {
                subscriptionManager.subscribeStaticAtStartup();
            } else {
                SubscriptionCoordinator coordinator = subscriptionCoordinatorProvider.getIfAvailable();
                subscribeExplicitly(brokerConnection, properties, subscriptions, coordinator);
            }
        }
        runtimeHealthState.markStartupCompleted();
        if (brokerConnection instanceof LoadBalancedBrokerGateway gateway) {
            log.info("Load-balanced broker gateway active with {} node(s)", gateway.connectionCount());
        }
    }

    private void verifyAnalyticsRestPreflight(IBrokerConnection brokerConnection, TradingProperties properties) {
        List<TradingProperties.SubscriptionProperties> configured = properties.subscriptions();
        InstrumentKey seed;
        if (configured != null && !configured.isEmpty()) {
            TradingProperties.SubscriptionProperties first = configured.get(0);
            seed = new InstrumentKey(first.symbol(), first.exchangeSegment());
        } else {
            seed = new InstrumentKey("SBIN", com.tradej.core.domain.value.ExchangeSegment.NSE_EQ);
        }
        verifyUpstoxPreflight(brokerConnection, seed);
    }

    private void loadCatalog(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            RuntimeHealthState runtimeHealthState,
            BrokerRuntimeMode mode
    ) {
        TradingProperties.InstrumentProperties instruments = properties.instruments();
        String csvPath = instruments == null ? null : instruments.csvPath();
        Path loadedPath = null;
        if (csvPath != null && !csvPath.isBlank()) {
            Path path = Path.of(csvPath);
            if (!Files.exists(path)) {
                throw new IllegalStateException("Instrument catalog file does not exist: " + path);
            }
            brokerConnection.loadInstrumentCatalog(path);
            loadedPath = path;
        } else if (instruments != null && instruments.autoDownload() && !mode.isUpstox()) {
            if (!(brokerConnection instanceof DhanBrokerConnection dhanConnection)) {
                throw new IllegalStateException("Dhan auto-download requires DhanBrokerConnection");
            }
            String cacheDirectory = instruments.cacheDirectory();
            if (cacheDirectory == null || cacheDirectory.isBlank()) {
                throw new IllegalStateException("Dhan runtime requires `trade.instruments.cache-directory` when auto-download is enabled");
            }
            loadedPath = dhanConnection.loadDailyInstrumentCatalog(Path.of(cacheDirectory), false);
        } else if (mode == BrokerRuntimeMode.BROKER_GATEWAY) {
            String cacheDirectory = instruments != null ? instruments.cacheDirectory() : null;
            if (cacheDirectory == null || cacheDirectory.isBlank()) {
                cacheDirectory = "runtime-prod/instruments";
            }
            Path cachePath = Path.of(cacheDirectory);
            brokerConnection.loadInstrumentCatalog(cachePath);
            loadedPath = cachePath;
        } else if (mode.isUpstox()) {
            String cacheDirectory = instruments != null ? instruments.cacheDirectory() : null;
            if (cacheDirectory == null || cacheDirectory.isBlank()) {
                cacheDirectory = "runtime-dev/upstox-instruments";
            }
            Path cachePath = Path.of(cacheDirectory);
            brokerConnection.loadInstrumentCatalog(cachePath);
            loadedPath = cachePath;
        } else if (mode.isIcici()) {
            String cacheDirectory = instruments != null ? instruments.cacheDirectory() : null;
            if (cacheDirectory == null || cacheDirectory.isBlank()) {
                cacheDirectory = "runtime/icici-instruments";
            }
            Path cachePath = Path.of(cacheDirectory);
            if (instruments != null && instruments.autoDownload()) {
                brokerConnection.loadInstrumentCatalog(null);
                loadedPath = cachePath;
            } else if (Files.exists(cachePath)) {
                brokerConnection.loadInstrumentCatalog(cachePath);
                loadedPath = cachePath;
            } else if (brokerConnection instanceof IciciBrokerConnection) {
                brokerConnection.loadInstrumentCatalog(null);
                loadedPath = cachePath;
            } else {
                throw new IllegalStateException("ICICI runtime requires instrument cache at " + cachePath + " or auto-download");
            }
        } else {
            throw new IllegalStateException("Runtime requires `trade.instruments.csv-path` or instrument auto-download");
        }
        if (brokerConnection.instruments().allInstruments().isEmpty()) {
            throw new IllegalStateException("Instrument catalog loaded zero instruments from " + loadedPath);
        }
        runtimeHealthState.markCatalogLoaded(brokerConnection.instruments().allInstruments().size());
    }

    private List<MarketSubscriptionRequest> validateSubscriptions(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
            boolean scanEnabled,
            BrokerRuntimeMode mode
    ) {
        List<TradingProperties.SubscriptionProperties> configured = properties.subscriptions();
        if (configured == null || configured.isEmpty()) {
            if (!scanEnabled && !mode.isUpstox()) {
                throw new IllegalStateException(
                        "Runtime requires at least one explicit market subscription, or enable trade.scan");
            }
            return List.of();
        }
        List<MarketSubscriptionRequest> requests = configured.stream()
                .map(subscription -> new MarketSubscriptionRequest(subscription.symbol(), Objects.requireNonNull(subscription.exchangeSegment(),
                        "Subscription exchange segment is required for " + subscription.symbol())))
                .toList();
        for (TradingProperties.SubscriptionProperties subscription : configured) {
            if (subscription.feedMode() == null) {
                throw new IllegalStateException("Subscription feed mode is required for " + subscription.symbol());
            }
            brokerCapabilities.validateFeedMode(subscription.exchangeSegment(), subscription.feedMode());
            if (brokerConnection instanceof DhanBrokerConnection) {
                DhanBrokerStartup.validateNoDepth200(subscription.exchangeSegment(), subscription.feedMode());
            }
            brokerConnection.instruments().resolve(new InstrumentKey(subscription.symbol(), subscription.exchangeSegment()));
        }
        return requests;
    }

    private void subscribeExplicitly(
            IBrokerConnection brokerConnection,
            TradingProperties properties,
            List<MarketSubscriptionRequest> subscriptions,
            SubscriptionCoordinator coordinator
    ) {
        Map<com.tradej.core.domain.value.FeedMode, List<MarketSubscriptionRequest>> byFeedMode = properties.subscriptions().stream()
                .collect(Collectors.groupingBy(
                        TradingProperties.SubscriptionProperties::feedMode,
                        Collectors.mapping(p -> new MarketSubscriptionRequest(p.symbol(), p.exchangeSegment()), Collectors.toList())
                ));
        if (subscriptions.isEmpty()) {
            throw new IllegalStateException("Refusing to start with zero active subscriptions");
        }
        if (coordinator != null) {
            byFeedMode.forEach((feedMode, requests) -> coordinator.subscribe(requests, feedMode));
        } else {
            byFeedMode.forEach((feedMode, requests) -> brokerConnection.websocket().subscribe(requests, feedMode));
        }
    }

    private void verifyBrokerPreflight(
            IBrokerConnection brokerConnection,
            List<MarketSubscriptionRequest> subscriptions,
            DhanApiEnvironment environment,
            BrokerRuntimeMode mode
    ) {
        if (subscriptions.isEmpty()) {
            throw new IllegalStateException("Broker preflight requires at least one validated subscription");
        }
        MarketSubscriptionRequest seed = subscriptions.get(0);
        InstrumentKey instrumentKey = new InstrumentKey(seed.symbol(), seed.exchangeSegment());
        if (mode.isUpstox()) {
            verifyUpstoxPreflight(brokerConnection, instrumentKey);
            return;
        }
        if (mode.isIcici()) {
            verifyIciciPreflight(brokerConnection, instrumentKey);
            return;
        }
        if (environment == DhanApiEnvironment.SANDBOX) {
            try {
                brokerConnection.portfolio().getBalance();
            } catch (RuntimeException ex) {
                log.warn("Broker preflight balance check skipped in sandbox: {}", ex.getMessage());
            }
        } else {
            brokerConnection.portfolio().getBalance();
        }
        try {
            brokerConnection.marketData().getQuote(instrumentKey);
        } catch (RuntimeException ex) {
            log.warn("Broker preflight quote check failed for {} (expected outside market hours): {}", instrumentKey, ex.getMessage());
        }
        LocalDate latestTradingDate = latestTradingDate();
        try {
            var candles = brokerConnection.marketData().getCandles(new CandleHistoryRequest(
                    instrumentKey,
                    "1d",
                    latestTradingDate.minusDays(7),
                    latestTradingDate
            ));
            if (candles.isEmpty()) {
                log.warn("Broker preflight historical request returned zero candles for {} (expected outside market hours)", instrumentKey);
            }
        } catch (RuntimeException ex) {
            log.warn("Broker preflight candle check failed for {} (expected outside market hours): {}", instrumentKey, ex.getMessage());
        }
    }

    private void verifyIciciPreflight(IBrokerConnection brokerConnection, InstrumentKey instrumentKey) {
        try {
            brokerConnection.portfolio().getBalance();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("ICICI preflight funds check failed", ex);
        }
        try {
            long ltp = brokerConnection.marketData().getLtpPaisa(instrumentKey);
            if (ltp <= 0) {
                log.warn("ICICI preflight LTP non-positive for {} (expected outside market hours)", instrumentKey);
            }
        } catch (RuntimeException ex) {
            log.warn("ICICI preflight quote check failed for {} (expected outside market hours): {}",
                    instrumentKey, ex.getMessage());
        }
        LocalDate latestTradingDate = latestTradingDate();
        try {
            var candles = brokerConnection.marketData().getCandles(new CandleHistoryRequest(
                    instrumentKey,
                    "1d",
                    latestTradingDate.minusDays(7),
                    latestTradingDate
            ));
            if (candles.isEmpty()) {
                log.warn("ICICI preflight historical request returned zero candles for {} (expected outside market hours)",
                        instrumentKey);
            }
        } catch (RuntimeException ex) {
            log.warn("ICICI preflight candle check failed for {} (expected outside market hours): {}",
                    instrumentKey, ex.getMessage());
        }
    }

    private void verifyUpstoxPreflight(IBrokerConnection brokerConnection, InstrumentKey instrumentKey) {
        try {
            long ltp = brokerConnection.marketData().getLtpPaisa(instrumentKey);
            if (ltp <= 0) {
                throw new IllegalStateException("Upstox preflight LTP must be positive for " + instrumentKey);
            }
        } catch (RuntimeException ex) {
            if (isAuthFailure(ex)) {
                throw ex;
            }
            log.warn("Upstox preflight LTP check failed for {} (expected outside market hours): {}",
                    instrumentKey, ex.getMessage());
        }
        LocalDate latestTradingDate = latestTradingDate();
        try {
            var candles = brokerConnection.marketData().getCandles(new CandleHistoryRequest(
                    instrumentKey,
                    "1d",
                    latestTradingDate.minusDays(7),
                    latestTradingDate
            ));
            if (candles.isEmpty()) {
                log.warn("Upstox preflight historical request returned zero candles for {} (expected outside market hours)",
                        instrumentKey);
            }
        } catch (RuntimeException ex) {
            if (isAuthFailure(ex)) {
                throw ex;
            }
            log.warn("Upstox preflight candle check failed for {} (expected outside market hours): {}",
                    instrumentKey, ex.getMessage());
        }
    }

    private static boolean isAuthFailure(RuntimeException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof UpstoxApiException api && api.isAuthFailure()) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private LocalDate latestTradingDate() {
        LocalDate date = LocalDate.now(INDIA);
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return date.minusDays(1);
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date.minusDays(2);
        }
        return date;
    }

    @SuppressWarnings({"removal", "deprecation"})
    private void subscribeEventHandlers(
            EventBus eventBus,
            AsyncDuckDbWriter asyncDuckDbWriter,
            ChronicleAuditLogWriter chronicleAuditLogWriter,
            AsyncDuckDbEventStore asyncDuckDbEventStore,
            BrokerErrorTracker brokerErrorTracker,
            ReadModelStore readModelStore,
            EventSourcedNetPositionProvider netPositionProvider,
            ReconciliationAlertLogger reconciliationAlertLogger,
            DagPipelineIngressBridge dagPipelineIngressBridge
    ) {
        // DuckDB feature store writes happen asynchronously on a dedicated thread
        // to avoid blocking the event dispatch thread with JDBC I/O (fixes FS-01).
        eventBus.subscribe(DomainEvent.class, asyncDuckDbWriter);
        eventBus.subscribe(DomainEvent.class, chronicleAuditLogWriter::onEvent);
        eventBus.subscribe(DomainEvent.class, asyncDuckDbEventStore::onEvent);
        eventBus.subscribe(BrokerAdapterError.class, error -> brokerErrorTracker.onEvent(error));

        eventBus.subscribe(OrderAccepted.class, readModelStore::onDomainEvent);
        eventBus.subscribe(OrderRejected.class, readModelStore::onDomainEvent);
        eventBus.subscribe(OrderFilled.class, readModelStore::onDomainEvent);
        eventBus.subscribe(TradeOpened.class, readModelStore::onDomainEvent);
        eventBus.subscribe(TradeClosed.class, readModelStore::onDomainEvent);
        eventBus.subscribe(MarketTickEvent.class, readModelStore::onDomainEvent);
        eventBus.subscribe(TickReceived.class, readModelStore::onDomainEvent);
        eventBus.subscribe(DepthUpdateEvent.class, readModelStore::onDomainEvent);
        eventBus.subscribe(CandleDeveloping.class, readModelStore::onDomainEvent);
        eventBus.subscribe(CandleClosed.class, readModelStore::onDomainEvent);
        eventBus.subscribe(SignalGenerated.class, readModelStore::onDomainEvent);
        eventBus.subscribe(PnlUpdatedEvent.class, readModelStore::onDomainEvent);

        eventBus.subscribe(TradeOpened.class, netPositionProvider::onDomainEvent);
        eventBus.subscribe(TradeClosed.class, netPositionProvider::onDomainEvent);

        eventBus.subscribe(PositionMismatch.class, reconciliationAlertLogger::onEvent);

        eventBus.subscribe(CandleClosed.class, dagPipelineIngressBridge::onEvent);
        eventBus.subscribe(MarketTickEvent.class, dagPipelineIngressBridge::onEvent);
        eventBus.subscribe(TickReceived.class, dagPipelineIngressBridge::onEvent);
    }

    private void setupWebSocketHandlers(
            IBrokerConnection brokerConnection,
            MarketDataPipeline marketDataPipeline,
            OrderPipeline orderPipeline,
            EventBus eventBus
    ) {
        brokerConnection.websocket().onMarketData(event -> {
            if (event instanceof MarketTickEvent tick) {
                marketDataPipeline.onMarketTickEvent(tick);
            } else {
                eventBus.publish(event);
            }
        });
        brokerConnection.websocket().onOrderUpdate(event -> {
            switch (event) {
                case OrderAccepted accepted -> orderPipeline.onOrderAccepted(accepted);
                case OrderFilled filled -> orderPipeline.onOrderFilled(filled);
                case com.tradej.core.domain.event.OrderPartiallyFilled partial ->
                        orderPipeline.onOrderPartiallyFilled(partial);
                case com.tradej.core.domain.event.OrderFullyFilled fully ->
                        orderPipeline.onOrderFullyFilled(fully);
                case OrderRejected rejected -> orderPipeline.onOrderRejected(rejected);
                default -> eventBus.publish(event);
            }
        });
    }
}
