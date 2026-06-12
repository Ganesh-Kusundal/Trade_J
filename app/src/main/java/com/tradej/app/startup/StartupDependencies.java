package com.tradej.app.startup;

import com.tradej.app.admin.RuntimeHealthState;
import com.tradej.app.health.BrokerErrorTracker;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.composition.config.ScanProperties;
import com.tradej.app.config.TradingProperties;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.service.PositionService;
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

/**
 * Aggregates all dependencies needed by {@link BrokerStartupOrchestrator}.
 * Replaces the previous 21-parameter method signature.
 *
 * <p>P3.4: the {@code netPositionProvider} field was renamed to {@code positionService}
 * and retyped from {@code EventSourcedNetPositionProvider} to the canonical
 * {@link PositionService}. Callers that need position state should use
 * {@code PositionService} (which is a {@code NetPositionProvider} subtype
 * for backward compat).
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
        PositionService positionService,
        DagPipelineIngressBridge dagPipelineIngressBridge,
        PositionStateRebuilder positionStateRebuilder,
        OrderManagementService orderManagementService,
        OrderReconciler orderReconciler
) {}
