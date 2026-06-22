package com.tradej.app.startup;

import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.app.health.BrokerErrorTracker;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.composition.config.ScanProperties;
import com.tradej.app.config.TradingProperties;
import com.tradej.core.domain.port.EventBus;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.execution.reconcile.ReconciliationUseCase;
import com.tradej.execution.readmodel.ReadModelStore;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.OrderManagementService;
import com.tradej.feature.store.AsyncDuckDbWriter;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import com.tradej.persistence.chronicle.ChronicleAuditLogWriter;
import com.tradej.persistence.duckdb.AsyncDuckDbEventStore;
import com.tradej.pipeline.service.DagPipelineIngressBridge;
import com.tradej.replay.engine.PositionStateRebuilder;

/**
 * Aggregates all dependencies needed by {@link BrokerStartupOrchestrator}.
 * Replaces the previous 21-parameter method signature.
 */
public record StartupDependencies(
        TradingProperties properties,
        ScanProperties scanProperties,
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
        PositionRiskHandler positionRiskHandler,
        DagPipelineIngressBridge dagPipelineIngressBridge,
        PositionStateRebuilder positionStateRebuilder,
        OrderManagementService orderManagementService,
        ReconciliationUseCase reconciliationUseCase
) {}
