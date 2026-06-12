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
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
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
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.service.PositionService;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public final class BrokerStartupOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BrokerStartupOrchestrator.class);

    private final Environment environment;
    private final TradingProperties tradingProperties;
    private final BrokerLifecycleManager lifecycleManager;
    private final List<BrokerStartupStrategy> strategies;

    public BrokerStartupOrchestrator(
            Environment environment,
            TradingProperties tradingProperties,
            BrokerLifecycleManager lifecycleManager
    ) {
        this.environment = environment;
        this.tradingProperties = tradingProperties;
        this.lifecycleManager = lifecycleManager;
        this.strategies = List.of(
                new UpstoxStartupStrategy(),
                new IciciStartupStrategy(),
                new GatewayStartupStrategy(),
                new SimulationStartupStrategy(),
                new DhanStartupStrategy()
        );
    }

    public void runStartup(
            StartupDependencies deps,
            ObjectProvider<DhanTokenProvider> dhanTokenProvider,
            ObjectProvider<BreezeTokenProvider> breezeTokenProvider,
            ObjectProvider<RuntimeSubscriptionManager> subscriptionManagerProvider,
            ObjectProvider<SubscriptionCoordinator> subscriptionCoordinatorProvider
    ) {
        BrokerTransportProfile profile = BrokerTransportProfile.resolve(environment, tradingProperties);
        BrokerStartupStrategy strategy = resolveStrategy(profile);
        List<MarketSubscriptionRequest> subscriptions = loadCatalogAndValidateSubscriptions(deps, profile, strategy);

        ensureBrokerTokens(deps, profile, dhanTokenProvider, breezeTokenProvider);
        runBrokerPreflight(deps, subscriptions, profile, strategy);
        wireRuntimeSubscribers(deps);
        wireWebSocketHandlers(deps, profile);
        startRuntimeAndRecover(deps);
        connectBrokerAndSubscribe(deps, profile, subscriptionManagerProvider, subscriptionCoordinatorProvider);
        markStartupCompleted(deps);
    }

    private List<MarketSubscriptionRequest> loadCatalogAndValidateSubscriptions(
            StartupDependencies deps,
            BrokerTransportProfile profile,
            BrokerStartupStrategy strategy
    ) {
        TradingProperties properties = deps.properties();
        IBrokerConnection brokerConnection = deps.brokerConnection();
        RuntimeHealthState runtimeHealthState = deps.runtimeHealthState();
        loadCatalog(properties, brokerConnection, runtimeHealthState, profile, strategy);
        boolean scanEnabled = deps.scanProperties() != null && deps.scanProperties().enabled();
        return validateSubscriptions(
                properties,
                brokerConnection,
                deps.brokerCapabilities(),
                scanEnabled,
                profile,
                strategy
        );
    }

    private void ensureBrokerTokens(
            StartupDependencies deps,
            BrokerTransportProfile profile,
            ObjectProvider<DhanTokenProvider> dhanTokenProvider,
            ObjectProvider<BreezeTokenProvider> breezeTokenProvider
    ) {
        if (!profile.isAnalyticsRest()) {
            dhanTokenProvider.ifAvailable(DhanTokenProvider::ensureValid);
            breezeTokenProvider.ifAvailable(BreezeTokenProvider::ensureValid);
        }
    }

    private void runBrokerPreflight(
            StartupDependencies deps,
            List<MarketSubscriptionRequest> subscriptions,
            BrokerTransportProfile profile,
            BrokerStartupStrategy strategy
    ) {
        if (!subscriptions.isEmpty()) {
            verifyBrokerPreflight(deps.brokerConnection(), subscriptions, profile, strategy);
        } else if (profile.isAnalyticsRest()) {
            verifyAnalyticsRestPreflight(deps.brokerConnection(), deps.properties(), strategy);
        }
        deps.runtimeHealthState().markBrokerPreflightPassed();
    }

    private void wireRuntimeSubscribers(StartupDependencies deps) {
        subscribeEventHandlers(
                deps.eventBus(),
                deps.asyncDuckDbWriter(),
                deps.chronicleAuditLogWriter(),
                deps.asyncDuckDbEventStore(),
                deps.brokerErrorTracker(),
                deps.readModelStore(),
                deps.positionService(),
                deps.reconciliationAlertLogger(),
                deps.dagPipelineIngressBridge()
        );
    }

    private void wireWebSocketHandlers(StartupDependencies deps, BrokerTransportProfile profile) {
        if (profile.expectsWebSocket()) {
            setupWebSocketHandlers(deps.brokerConnection(), deps.marketDataPipeline(), deps.orderPipeline(), deps.eventBus());
        } else {
            log.info("Skipping WebSocket handler wiring in REST-only mode.");
        }
    }

    private void startRuntimeAndRecover(StartupDependencies deps) {
        deps.eventBus().start();
        deps.orderManagementService().replayAll();
        try {
            deps.orderReconciler().reconcileAll(deps.eventBus()::publish);
            log.info("OMS crash recovery reconciliation completed");
        } catch (Exception e) {
            log.error("OMS reconciliation failed during startup — manual intervention may be required", e);
        }
        deps.positionStateRebuilder().rebuild(deps.eventBus());
    }

    private void connectBrokerAndSubscribe(
            StartupDependencies deps,
            BrokerTransportProfile profile,
            ObjectProvider<RuntimeSubscriptionManager> subscriptionManagerProvider,
            ObjectProvider<SubscriptionCoordinator> subscriptionCoordinatorProvider
    ) {
        if (!profile.expectsWebSocket()) {
            log.warn("Skipping broker WebSocket connect. REST APIs remain available.");
            return;
        }
        deps.brokerConnection().connect();
        RuntimeSubscriptionManager subscriptionManager = subscriptionManagerProvider.getIfAvailable();
        if (subscriptionManager != null) {
            subscriptionManager.subscribeStaticAtStartup();
        } else {
            SubscriptionCoordinator coordinator = subscriptionCoordinatorProvider.getIfAvailable();
            subscribeExplicitly(deps.brokerConnection(), deps.properties(),
                    validateSubscriptions(
                            deps.properties(),
                            deps.brokerConnection(),
                            deps.brokerCapabilities(),
                            deps.scanProperties() != null && deps.scanProperties().enabled(),
                            profile,
                            resolveStrategy(profile)
                    ),
                    coordinator);
        }
    }

    private void markStartupCompleted(StartupDependencies deps) {
        deps.runtimeHealthState().markStartupCompleted();
        if (deps.brokerConnection() instanceof LoadBalancedBrokerGateway gateway) {
            log.info("Load-balanced broker gateway active with {} node(s)", gateway.connectionCount());
        }
    }

    private BrokerStartupStrategy resolveStrategy(BrokerTransportProfile profile) {
        return strategies.stream()
                .filter(s -> s.matches(profile))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No startup strategy found for broker transport profile: " + profile));
    }

    private void loadCatalog(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            RuntimeHealthState runtimeHealthState,
            BrokerTransportProfile profile,
            BrokerStartupStrategy strategy
    ) {
        strategy.loadCatalog(properties, brokerConnection, lifecycleManager, profile);
        runtimeHealthState.markCatalogLoaded(brokerConnection.instruments().allInstruments().size());
    }

    private List<MarketSubscriptionRequest> validateSubscriptions(
            TradingProperties properties,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
            boolean scanEnabled,
            BrokerTransportProfile profile,
            BrokerStartupStrategy strategy
    ) {
        List<TradingProperties.SubscriptionProperties> configured = properties.subscriptions();
        strategy.validateSubscriptions(configured, brokerConnection, brokerCapabilities, scanEnabled, profile);
        if (configured == null || configured.isEmpty()) {
            return List.of();
        }
        List<BrokerLifecycleManager.SubscriptionConfig> configs = configured.stream()
                .map(sub -> new BrokerLifecycleManager.SubscriptionConfig(
                        sub.symbol(), sub.exchangeSegment(), sub.feedMode()))
                .toList();
        return lifecycleManager.validateSubscriptions(configs, brokerCapabilities, brokerConnection);
    }

    private void verifyAnalyticsRestPreflight(
            IBrokerConnection brokerConnection,
            TradingProperties properties,
            BrokerStartupStrategy strategy
    ) {
        List<TradingProperties.SubscriptionProperties> configured = properties.subscriptions();
        InstrumentKey seed;
        if (configured != null && !configured.isEmpty()) {
            TradingProperties.SubscriptionProperties first = configured.get(0);
            seed = new InstrumentKey(first.symbol(), first.exchangeSegment());
        } else {
            seed = new InstrumentKey("SBIN", com.tradej.core.domain.value.ExchangeSegment.NSE_EQ);
        }
        strategy.verifyPreflight(brokerConnection, seed, lifecycleManager, null);
    }

    private void verifyBrokerPreflight(
            IBrokerConnection brokerConnection,
            List<MarketSubscriptionRequest> subscriptions,
            BrokerTransportProfile profile,
            BrokerStartupStrategy strategy
    ) {
        MarketSubscriptionRequest seed = subscriptions.get(0);
        InstrumentKey instrumentKey = new InstrumentKey(seed.symbol(), seed.exchangeSegment());
        strategy.verifyPreflight(brokerConnection, instrumentKey, lifecycleManager, profile);
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

    private void subscribeEventHandlers(
            EventBus eventBus,
            AsyncDuckDbWriter asyncDuckDbWriter,
            ChronicleAuditLogWriter chronicleAuditLogWriter,
            AsyncDuckDbEventStore asyncDuckDbEventStore,
            BrokerErrorTracker brokerErrorTracker,
            ReadModelStore readModelStore,
            PositionService positionService,
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

        // P3.4: PositionService is the canonical event-sourced position source.
        eventBus.subscribe(TradeOpened.class, positionService::onDomainEvent);
        eventBus.subscribe(TradeClosed.class, positionService::onDomainEvent);

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
