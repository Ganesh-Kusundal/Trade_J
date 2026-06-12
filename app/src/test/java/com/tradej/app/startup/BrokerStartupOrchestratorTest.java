package com.tradej.app.startup;

import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.app.config.TradingProperties;
import com.tradej.app.health.BrokerErrorTracker;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.core.startup.BrokerLifecycleManager;
import com.tradej.core.domain.port.EventBus;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.reconcile.OrderReconciler;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.execution.readmodel.ReadModelStore;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.feature.store.AsyncDuckDbWriter;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import com.tradej.pipeline.service.DagPipelineIngressBridge;
import com.tradej.replay.engine.PositionStateRebuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class BrokerStartupOrchestratorTest {

    @Test
    void simulationStartupRunsRuntimeRecoveryBeforeMarkingStartupComplete() {
        RuntimeHealthState runtimeHealthState = new RuntimeHealthState();
        EventBus eventBus = mock(EventBus.class);
        OrderManagementService orderManagementService = mock(OrderManagementService.class);
        OrderReconciler orderReconciler = mock(OrderReconciler.class);
        PositionStateRebuilder positionStateRebuilder = mock(PositionStateRebuilder.class);
        IBrokerConnection brokerConnection = brokerConnectionWithInstruments();
        BrokerStartupOrchestrator orchestrator = new BrokerStartupOrchestrator(
                env("simulation"),
                tradingProperties(),
                new BrokerLifecycleManager()
        );

        orchestrator.runStartup(
                startupDependencies(runtimeHealthState, eventBus, brokerConnection, orderManagementService,
                        orderReconciler, positionStateRebuilder),
                emptyProvider(),
                emptyProvider(),
                emptyProvider(),
                emptyProvider()
        );

        InOrder startupOrder = inOrder(
                eventBus,
                orderManagementService,
                orderReconciler,
                positionStateRebuilder
        );
        startupOrder.verify(eventBus).start();
        startupOrder.verify(orderManagementService).replayAll();
        startupOrder.verify(orderReconciler).reconcileAll(any());
        startupOrder.verify(positionStateRebuilder).rebuild(eventBus);
        assertTrue(runtimeHealthState.catalogLoaded());
        assertTrue(runtimeHealthState.brokerPreflightPassed());
        assertTrue(runtimeHealthState.startupCompleted());
        verify(brokerConnection, never()).connect();
        verify(brokerConnection, never()).websocket();
    }

    private static StartupDependencies startupDependencies(
            RuntimeHealthState runtimeHealthState,
            EventBus eventBus,
            IBrokerConnection brokerConnection,
            OrderManagementService orderManagementService,
            OrderReconciler orderReconciler,
            PositionStateRebuilder positionStateRebuilder
    ) {
        return new StartupDependencies(
                tradingProperties(),
                null,
                brokerConnection,
                mock(BrokerCapabilities.class),
                runtimeHealthState,
                eventBus,
                mock(MarketDataPipeline.class),
                mock(OrderPipeline.class),
                mock(AsyncDuckDbWriter.class),
                mock(ChronicleAuditLogWriter.class),
                mock(AsyncDuckDbEventStore.class),
                mock(ReconciliationAlertLogger.class),
                mock(BrokerErrorTracker.class),
                mock(ReadModelStore.class),
                mock(EventSourcedNetPositionProvider.class),
                mock(DagPipelineIngressBridge.class),
                positionStateRebuilder,
                orderManagementService,
                orderReconciler
        );
    }

    private static IBrokerConnection brokerConnectionWithInstruments() {
        IBrokerConnection brokerConnection = mock(IBrokerConnection.class);
        InstrumentResolver instruments = mock(InstrumentResolver.class);
        when(instruments.allInstruments()).thenReturn(List.of());
        when(brokerConnection.instruments()).thenReturn(instruments);
        return brokerConnection;
    }

    private static MockEnvironment env(String brokerType) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("trade.broker-type", brokerType);
        return env;
    }

    private static TradingProperties tradingProperties() {
        return new TradingProperties(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null
        );
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
