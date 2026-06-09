package com.tradej.app.config;

import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.app.health.BrokerErrorTracker;
import com.tradej.app.startup.BrokerStartupOrchestrator;
import com.tradej.app.startup.StartupDependencies;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.composition.config.ScanProperties;
import com.tradej.core.domain.port.EventBus;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.readmodel.ReadModelStore;
import com.tradej.execution.reconcile.OrderReconciler;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.execution.subscription.SubscriptionCoordinator;
import com.tradej.app.scanner.RuntimeSubscriptionManager;
import com.tradej.feature.store.AsyncDuckDbWriter;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import com.tradej.pipeline.service.DagPipelineIngressBridge;
import com.tradej.replay.engine.PositionStateRebuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Configures runtime startup orchestration and health state tracking.
 */
@Configuration
public class StartupConfiguration {

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
