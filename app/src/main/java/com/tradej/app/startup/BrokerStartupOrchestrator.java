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
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.execution.reconcile.ReconciliationUseCase;
import com.tradej.execution.risk.PositionRiskHandler;
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
import java.util.function.Supplier;
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
        TradingProperties properties = deps.properties();
        IBrokerConnection brokerConnection = deps.brokerConnection();
        BrokerCapabilities brokerCapabilities = deps.brokerCapabilities();
        RuntimeHealthState runtimeHealthState = deps.runtimeHealthState();
        EventBus eventBus = deps.eventBus();
        MarketDataPipeline marketDataPipeline = deps.marketDataPipeline();
        OrderPipeline orderPipeline = deps.orderPipeline();
        AsyncDuckDbWriter asyncDuckDbWriter = deps.asyncDuckDbWriter();
        ChronicleAuditLogWriter chronicleAuditLogWriter = deps.chronicleAuditLogWriter();
        AsyncDuckDbEventStore asyncDuckDbEventStore = deps.asyncDuckDbEventStore();
        ReconciliationAlertLogger reconciliationAlertLogger = deps.reconciliationAlertLogger();
        BrokerErrorTracker brokerErrorTracker = deps.brokerErrorTracker();
        ReadModelStore readModelStore = deps.readModelStore();
        EventSourcedNetPositionProvider netPositionProvider = deps.netPositionProvider();
        PositionRiskHandler positionRiskHandler = deps.positionRiskHandler();
        DagPipelineIngressBridge dagPipelineIngressBridge = deps.dagPipelineIngressBridge();
        PositionStateRebuilder positionStateRebuilder = deps.positionStateRebuilder();
        OrderManagementService orderManagementService = deps.orderManagementService();
        ReconciliationUseCase reconciliationUseCase = deps.reconciliationUseCase();

        StartupPlan plan = runPhase(BrokerStartupPhase.RESOLVE_PROFILE, () -> {
            BrokerTransportProfile profile = BrokerTransportProfile.resolve(environment, tradingProperties);
            return new StartupPlan(
                    profile,
                    resolveStrategy(profile),
                    deps.scanProperties() != null && deps.scanProperties().enabled()
            );
        });

        runPhase(BrokerStartupPhase.LOAD_CATALOG,
                () -> loadCatalog(properties, brokerConnection, runtimeHealthState, plan.profile(), plan.strategy()));
        List<MarketSubscriptionRequest> subscriptions = runPhase(BrokerStartupPhase.VALIDATE_SUBSCRIPTIONS,
                () -> validateSubscriptions(properties, brokerConnection, brokerCapabilities,
                        plan.scanEnabled(), plan.profile(), plan.strategy()));

        runPhase(BrokerStartupPhase.VALIDATE_TOKENS, () -> validateTokens(
                plan.profile(), dhanTokenProvider, breezeTokenProvider));

        runPhase(BrokerStartupPhase.VERIFY_PREFLIGHT, () -> {
            verifyPreflight(brokerConnection, properties, subscriptions, plan.profile(), plan.strategy());
            runtimeHealthState.markBrokerPreflightPassed();
        });

        runPhase(BrokerStartupPhase.WIRE_EVENT_HANDLERS, () -> {
            subscribeEventHandlers(eventBus, asyncDuckDbWriter, chronicleAuditLogWriter,
                    asyncDuckDbEventStore, brokerErrorTracker, readModelStore,
                    netPositionProvider, positionRiskHandler, reconciliationAlertLogger, dagPipelineIngressBridge);
            if (plan.profile().expectsWebSocket()) {
                setupWebSocketHandlers(brokerConnection, marketDataPipeline, orderPipeline, eventBus);
            } else {
                log.info("Skipping WebSocket handler wiring in REST-only mode.");
            }
        });

        runPhase(BrokerStartupPhase.START_EVENT_BUS, eventBus::start);
        runPhase(BrokerStartupPhase.RECOVER_STATE, () -> recoverState(
                eventBus, orderManagementService, reconciliationUseCase, positionStateRebuilder));
        runPhase(BrokerStartupPhase.CONNECT_TRANSPORT, () -> connectTransport(
                brokerConnection, properties, subscriptions, subscriptionManagerProvider,
                subscriptionCoordinatorProvider, plan.profile()));
        runPhase(BrokerStartupPhase.COMPLETE, () -> completeStartup(runtimeHealthState, brokerConnection));
    }

    private <T> T runPhase(BrokerStartupPhase phase, Supplier<T> action) {
        log.info("Broker startup phase started: {}", phase);
        try {
            T result = action.get();
            log.info("Broker startup phase completed: {}", phase);
            return result;
        } catch (RuntimeException ex) {
            log.error("Broker startup phase failed: {}", phase, ex);
            throw ex;
        }
    }

    private void runPhase(BrokerStartupPhase phase, Runnable action) {
        runPhase(phase, () -> {
            action.run();
            return null;
        });
    }

    private void validateTokens(
            BrokerTransportProfile profile,
            ObjectProvider<DhanTokenProvider> dhanTokenProvider,
            ObjectProvider<BreezeTokenProvider> breezeTokenProvider
    ) {
        if (!profile.isAnalyticsRest()) {
            dhanTokenProvider.ifAvailable(DhanTokenProvider::ensureValid);
            breezeTokenProvider.ifAvailable(BreezeTokenProvider::ensureValid);
        }
    }

    private void verifyPreflight(
            IBrokerConnection brokerConnection,
            TradingProperties properties,
            List<MarketSubscriptionRequest> subscriptions,
            BrokerTransportProfile profile,
            BrokerStartupStrategy strategy
    ) {
        if (!subscriptions.isEmpty()) {
            verifyBrokerPreflight(brokerConnection, subscriptions, profile, strategy);
        } else if (profile.isAnalyticsRest()) {
            verifyAnalyticsRestPreflight(brokerConnection, properties, strategy);
        }
    }

    private void recoverState(
            EventBus eventBus,
            OrderManagementService orderManagementService,
            ReconciliationUseCase reconciliationUseCase,
            PositionStateRebuilder positionStateRebuilder
    ) {
        orderManagementService.replayAll();
        reconciliationUseCase.reconcileOmsProjection();
        log.info("Startup OSM reconciliation completed");
        positionStateRebuilder.rebuild(eventBus);
    }

    private void connectTransport(
            IBrokerConnection brokerConnection,
            TradingProperties properties,
            List<MarketSubscriptionRequest> subscriptions,
            ObjectProvider<RuntimeSubscriptionManager> subscriptionManagerProvider,
            ObjectProvider<SubscriptionCoordinator> subscriptionCoordinatorProvider,
            BrokerTransportProfile profile
    ) {
        if (!profile.expectsWebSocket()) {
            log.warn("Skipping broker WebSocket connect. REST APIs remain available.");
            return;
        }
        brokerConnection.connect();
        RuntimeSubscriptionManager subscriptionManager = subscriptionManagerProvider.getIfAvailable();
        if (subscriptionManager != null) {
            subscriptionManager.subscribeStaticAtStartup();
        } else {
            SubscriptionCoordinator coordinator = subscriptionCoordinatorProvider.getIfAvailable();
            subscribeExplicitly(brokerConnection, properties, subscriptions, coordinator);
        }
    }

    private void completeStartup(RuntimeHealthState runtimeHealthState, IBrokerConnection brokerConnection) {
        runtimeHealthState.markStartupCompleted();
        if (brokerConnection instanceof LoadBalancedBrokerGateway gateway) {
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

    private record StartupPlan(
            BrokerTransportProfile profile,
            BrokerStartupStrategy strategy,
            boolean scanEnabled
    ) {
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
            seed = InstrumentKey.of(first.symbol(), first.exchangeSegment());
        } else {
            seed = InstrumentKey.of("SBIN", com.tradej.core.domain.value.ExchangeSegment.NSE_EQ);
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
        InstrumentKey instrumentKey = seed.key();
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
            EventSourcedNetPositionProvider netPositionProvider,
            PositionRiskHandler positionRiskHandler,
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
        eventBus.subscribe(TradeOpened.class, event -> positionRiskHandler.onDomainEvent(event, eventBus::publish));
        eventBus.subscribe(TradeClosed.class, event -> positionRiskHandler.onDomainEvent(event, eventBus::publish));

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
