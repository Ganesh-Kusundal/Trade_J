package com.tradej.app.startup;

import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.app.config.BrokerTransportProfile;
import com.tradej.composition.config.ScanProperties;
import com.tradej.app.config.TradingProperties;
import org.springframework.core.env.Environment;
import com.tradej.app.health.BrokerErrorTracker;
import com.tradej.pipeline.service.DagPipelineIngressBridge;
import com.tradej.replay.engine.PositionStateRebuilder;
import com.tradej.execution.readmodel.ReadModelStore;
import com.tradej.app.scanner.RuntimeSubscriptionManager;
import com.tradej.execution.subscription.SubscriptionCoordinator;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
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
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.EventBus;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.reconcile.OrderReconciler;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.feature.store.AsyncDuckDbWriter;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public final class BrokerStartupOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BrokerStartupOrchestrator.class);

    private final Environment environment;
    private final TradingProperties tradingProperties;
    private final BrokerLifecycleManager lifecycleManager;

    public BrokerStartupOrchestrator(
            Environment environment,
            TradingProperties tradingProperties,
            BrokerLifecycleManager lifecycleManager
    ) {
        this.environment = environment;
        this.tradingProperties = tradingProperties;
        this.lifecycleManager = lifecycleManager;
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
            OrderManagementService orderManagementService,
            OrderReconciler orderReconciler
    ) {
        BrokerTransportProfile profile = BrokerTransportProfile.resolve(environment, tradingProperties);

        loadCatalog(properties, brokerConnection, runtimeHealthState, profile);
        boolean scanEnabled = scanPropertiesProvider.getIfAvailable() != null
                && scanPropertiesProvider.getIfAvailable().enabled();
        List<MarketSubscriptionRequest> subscriptions = validateSubscriptions(
                properties, brokerConnection, brokerCapabilities, scanEnabled, profile);

        if (!profile.isAnalyticsRest()) {
            dhanTokenProvider.ifAvailable(DhanTokenProvider::ensureValid);
            breezeTokenProvider.ifAvailable(BreezeTokenProvider::ensureValid);
        }

        if (!subscriptions.isEmpty()) {
            DhanApiEnvironment env = properties.broker() != null
                    ? properties.broker().environment()
                    : DhanApiEnvironment.LIVE;
            verifyBrokerPreflight(brokerConnection, subscriptions, env, profile);
        } else if (profile.isAnalyticsRest()) {
            verifyAnalyticsRestPreflight(brokerConnection, properties);
        }
        runtimeHealthState.markBrokerPreflightPassed();

        subscribeEventHandlers(eventBus, asyncDuckDbWriter, chronicleAuditLogWriter,
                asyncDuckDbEventStore, brokerErrorTracker, readModelStore,
                netPositionProvider, reconciliationAlertLogger, dagPipelineIngressBridge);
        if (profile.expectsWebSocket()) {
            setupWebSocketHandlers(brokerConnection, marketDataPipeline, orderPipeline, eventBus);
        } else {
            log.info("Skipping WebSocket handler wiring in REST-only mode.");
        }

        eventBus.start();
        orderManagementService.replayAll();
        try {
            orderReconciler.reconcileAll(eventBus::publish);
            log.info("OMS crash recovery reconciliation completed");
        } catch (Exception e) {
            log.error("OMS reconciliation failed during startup — manual intervention may be required", e);
        }
        positionStateRebuilder.rebuild(eventBus);

        if (!profile.expectsWebSocket()) {
            log.warn("Skipping broker WebSocket connect. REST APIs remain available.");
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

    private void loadCatalog(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            RuntimeHealthState runtimeHealthState,
            BrokerTransportProfile profile
    ) {
        TradingProperties.InstrumentProperties instruments = properties.instruments();
        String csvPath = instruments == null ? null : instruments.csvPath();
        Path loadedPath = null;
        if (csvPath != null && !csvPath.isBlank()) {
            loadedPath = Path.of(csvPath);
            lifecycleManager.loadInstrumentCatalog(brokerConnection, loadedPath);
        } else if (instruments != null && instruments.autoDownload() && !profile.isUpstox()) {
            if (!(brokerConnection instanceof DhanBrokerConnection dhanConnection)) {
                throw new IllegalStateException("Dhan auto-download requires DhanBrokerConnection");
            }
            String cacheDirectory = instruments.cacheDirectory();
            if (cacheDirectory == null || cacheDirectory.isBlank()) {
                throw new IllegalStateException("Dhan runtime requires `trade.instruments.cache-directory` when auto-download is enabled");
            }
            loadedPath = dhanConnection.loadDailyInstrumentCatalog(Path.of(cacheDirectory), false);
        } else if (profile.gateway()) {
            String cacheDirectory = instruments != null ? instruments.cacheDirectory() : null;
            if (cacheDirectory == null || cacheDirectory.isBlank()) {
                cacheDirectory = "runtime-prod/instruments";
            }
            loadedPath = Path.of(cacheDirectory);
            lifecycleManager.loadInstrumentCatalog(brokerConnection, loadedPath);
        } else if (profile.isUpstox()) {
            String cacheDirectory = instruments != null ? instruments.cacheDirectory() : null;
            if (cacheDirectory == null || cacheDirectory.isBlank()) {
                cacheDirectory = "runtime-dev/upstox-instruments";
            }
            loadedPath = Path.of(cacheDirectory);
            lifecycleManager.loadInstrumentCatalog(brokerConnection, loadedPath);
        } else if (profile.isIcici()) {
            String cacheDirectory = instruments != null ? instruments.cacheDirectory() : null;
            if (cacheDirectory == null || cacheDirectory.isBlank()) {
                cacheDirectory = "runtime/icici-instruments";
            }
            Path cachePath = Path.of(cacheDirectory);
            if (instruments != null && instruments.autoDownload()) {
                brokerConnection.loadInstrumentCatalog(null);
                loadedPath = cachePath;
            } else if (java.nio.file.Files.exists(cachePath)) {
                lifecycleManager.loadInstrumentCatalog(brokerConnection, cachePath);
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
        runtimeHealthState.markCatalogLoaded(brokerConnection.instruments().allInstruments().size());
    }

    private List<MarketSubscriptionRequest> validateSubscriptions(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
            boolean scanEnabled,
            BrokerTransportProfile profile
    ) {
        List<TradingProperties.SubscriptionProperties> configured = properties.subscriptions();
        if (configured == null || configured.isEmpty()) {
            if (!scanEnabled && !profile.isUpstox()) {
                throw new IllegalStateException(
                        "Runtime requires at least one explicit market subscription, or enable trade.scan");
            }
            return List.of();
        }
        List<BrokerLifecycleManager.SubscriptionConfig> configs = configured.stream()
                .map(sub -> new BrokerLifecycleManager.SubscriptionConfig(
                        sub.symbol(), sub.exchangeSegment(), sub.feedMode()))
                .toList();
        List<MarketSubscriptionRequest> requests = lifecycleManager.validateSubscriptions(
                configs, brokerCapabilities, brokerConnection);
        if (brokerConnection instanceof DhanBrokerConnection) {
            for (TradingProperties.SubscriptionProperties subscription : configured) {
                DhanBrokerStartup.validateNoDepth200(subscription.exchangeSegment(), subscription.feedMode());
            }
        }
        return requests;
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
            BrokerTransportProfile profile
    ) {
        MarketSubscriptionRequest seed = subscriptions.get(0);
        InstrumentKey instrumentKey = new InstrumentKey(seed.symbol(), seed.exchangeSegment());
        if (profile.isUpstox()) {
            verifyUpstoxPreflight(brokerConnection, instrumentKey);
            return;
        }
        if (profile.isIcici()) {
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
            lifecycleManager.verifyPreflight(brokerConnection, instrumentKey);
        }
    }

    private void verifyIciciPreflight(IBrokerConnection brokerConnection, InstrumentKey instrumentKey) {
        try {
            brokerConnection.portfolio().getBalance();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("ICICI preflight funds check failed", ex);
        }
        lifecycleManager.verifyPreflight(brokerConnection, instrumentKey);
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
        LocalDate latestTradingDate = BrokerLifecycleManager.latestTradingDate();
        try {
            var candles = brokerConnection.marketData().getCandles(new CandleHistoryRequest(
                    instrumentKey, "1d",
                    latestTradingDate.minusDays(7), latestTradingDate
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
