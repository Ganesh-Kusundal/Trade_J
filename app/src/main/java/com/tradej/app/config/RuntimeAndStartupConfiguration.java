package com.tradej.app.config;

import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.app.health.BrokerErrorTracker;
import com.tradej.app.health.MarketDataHealthIndicator;
import com.tradej.app.startup.BrokerStartupOrchestrator;
import com.tradej.app.startup.StartupDependencies;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.composition.config.ScanProperties;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.core.domain.runtime.RuntimeBus;
import com.tradej.core.domain.runtime.RuntimeBusHolder;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.ReplayTradingClock;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.disruptor.DisruptorEventBus;
import com.tradej.disruptor.config.BrokerScopedEventBus;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.readmodel.ReadModelStore;
import com.tradej.execution.reconcile.OrderReconciler;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.subscription.SubscriptionCoordinator;
import com.tradej.app.scanner.RuntimeSubscriptionManager;
import com.tradej.feature.store.AsyncDuckDbWriter;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import com.tradej.pipeline.service.DagPipelineIngressBridge;
import com.tradej.pipeline.runtime.PipelineRuntimeBridge;
import com.tradej.replay.engine.PositionStateRebuilder;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import com.tradej.disruptor.config.StageTimings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Unified runtime and startup configuration consolidating runtime mode,
 * event bus, pipeline, and startup orchestration beans.
 */
@Configuration
public class RuntimeAndStartupConfiguration {

    // ── Time and clocks ──

    @Bean
    @Profile("!replay")
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @Primary
    @Profile("!replay")
    public TradingClock liveTradingClock() {
        return new LiveTradingClock();
    }

    @Bean
    @Profile("replay")
    public TradingClock replayTradingClock() {
        return new ReplayTradingClock(Instant.EPOCH);
    }

    @Bean
    public EventMetadataFactory eventMetadataFactory(TradingClock tradingClock) {
        return new EventMetadataFactory(tradingClock);
    }

    // ── Runtime mode ──

    @Bean
    RuntimeModeHolder runtimeModeHolder(TradingProperties properties) {
        RuntimeModeHolder holder = new RuntimeModeHolder();
        if (properties.runtime() != null) {
            holder.setMode(properties.runtime().mode());
        }
        return holder;
    }

    @Bean
    RuntimeBusHolder runtimeBusHolder(TradingProperties properties) {
        RuntimeBusHolder holder = new RuntimeBusHolder();
        if (properties.runtime() != null) {
            holder.setMode(properties.runtime().bus());
        }
        return holder;
    }

    // ── Event bus ──

    @Lazy
    @Bean
    @Primary
    EventBus eventBus(
            TradingProperties properties,
            RuntimeBusHolder runtimeBusHolder,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            StageTimings stageTimings,
            FeatureStore featureStore,
            DeadLetterQueue deadLetterQueue,
            PipelineRuntimeBridge pipelineRuntimeBridge
    ) {
        RuntimeBus bus = properties.runtime() == null ? RuntimeBus.SIMPLE : properties.runtime().bus();
        runtimeBusHolder.setMode(bus);
        if (bus == RuntimeBus.DISRUPTOR) {
            DisruptorEventBus disruptorEventBus = new DisruptorEventBus(
                    new com.tradej.disruptor.config.DisruptorPipelineConfig(
                            positionRiskHandler,
                            candleAggregationService,
                            graphStrategySandbox,
                            executionHandler,
                            portfolioEngine,
                            stageTimings,
                            featureStore,
                            deadLetterQueue,
                            pipelineRuntimeBridge,
                            true,
                            properties.runtime() == null ? com.tradej.core.domain.runtime.RuntimeMode.LIVE : properties.runtime().mode(),
                            com.tradej.core.domain.port.EventWriteAheadLog.noop()
                    )
            );
            return disruptorEventBus;
        }
        return new SimpleEventBus();
    }

    @Lazy
    @Bean("dhanEventBus")
    BrokerScopedEventBus dhanEventBus(EventBus eventBus) {
        return new BrokerScopedEventBus(eventBus, "dhan");
    }

    @Lazy
    @Bean("upstoxEventBus")
    BrokerScopedEventBus upstoxEventBus(EventBus eventBus) {
        return new BrokerScopedEventBus(eventBus, "upstox");
    }

    @Lazy
    @Bean("iciciEventBus")
    BrokerScopedEventBus iciciEventBus(EventBus eventBus) {
        return new BrokerScopedEventBus(eventBus, "icici");
    }

    @Lazy
    @Bean
    DisruptorBusMetrics disruptorBusMetrics(EventBus eventBus) {
        if (eventBus instanceof DisruptorBusMetrics metrics) {
            return metrics;
        }
        if (eventBus instanceof SimpleEventBus simple) {
            return new SimpleBusMetrics(simple);
        }
        return new NoOpBusMetrics();
    }

    @Lazy
    @Bean
    MarketDataPipeline marketDataPipeline(EventBus eventBus) {
        return new MarketDataPipeline((Consumer<DomainEvent>) eventBus::publish);
    }

    @Lazy
    @Bean
    OrderPipeline orderPipeline(EventBus eventBus) {
        return new OrderPipeline((Consumer<DomainEvent>) eventBus::publish);
    }

    @Bean
    MarketDataHealthIndicator marketDataHealthIndicator(
            @Lazy MarketDataPipeline marketDataPipeline,
            ObjectProvider<com.tradej.broker.api.model.BrokerTransportCapabilities> transportCapabilitiesProvider,
            com.tradej.app.health.AlertManager alertManager
    ) {
        return new MarketDataHealthIndicator(marketDataPipeline, transportCapabilitiesProvider, alertManager);
    }

    // ── Startup orchestration ──

    @Bean
    RuntimeHealthState runtimeHealthState() {
        return new RuntimeHealthState();
    }

    @Bean
    BrokerLifecycleManager brokerLifecycleManager() {
        return new BrokerLifecycleManager();
    }

    @Bean
    StartupDependencies startupDependencies(
            Environment environment,
            TradingProperties properties,
            ObjectProvider<ScanProperties> scanPropertiesProvider,
            IBrokerConnection brokerConnection,
            BrokerCapabilities brokerCapabilities,
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
            DagPipelineIngressBridge dagPipelineIngressBridge,
            PositionStateRebuilder positionStateRebuilder,
            OrderManagementService orderManagementService,
            OrderReconciler orderReconciler
    ) {
        return new StartupDependencies(
                properties,
                scanPropertiesProvider.getIfAvailable(),
                brokerConnection,
                brokerCapabilities,
                runtimeHealthState,
                eventBus,
                marketDataPipeline,
                orderPipeline,
                asyncDuckDbWriter,
                chronicleAuditLogWriter,
                asyncDuckDbEventStore,
                reconciliationAlertLogger,
                brokerErrorTracker,
                readModelStore,
                netPositionProvider,
                dagPipelineIngressBridge,
                positionStateRebuilder,
                orderManagementService,
                orderReconciler
        );
    }

    @Bean
    ApplicationRunner runtimeStarter(
            StartupDependencies deps,
            ObjectProvider<DhanTokenProvider> dhanTokenProvider,
            ObjectProvider<BreezeTokenProvider> breezeTokenProvider,
            ObjectProvider<RuntimeSubscriptionManager> subscriptionManagerProvider,
            ObjectProvider<SubscriptionCoordinator> subscriptionCoordinatorProvider,
            BrokerStartupOrchestrator brokerStartupOrchestrator
    ) {
        return args -> brokerStartupOrchestrator.runStartup(
                deps,
                dhanTokenProvider,
                breezeTokenProvider,
                subscriptionManagerProvider,
                subscriptionCoordinatorProvider
        );
    }
}
