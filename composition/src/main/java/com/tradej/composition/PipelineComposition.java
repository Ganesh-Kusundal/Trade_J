package com.tradej.composition;

import com.tradej.core.domain.port.FeatureStore;
import com.tradej.core.domain.config.TradeDefaults;
import com.tradej.core.domain.runtime.ExecutionModePolicy;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.reactor.ReactorBridge;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.service.DagPipelineIngressBridge;
import com.tradej.pipeline.service.DagPipelineRuntimeService;
import com.tradej.pipeline.service.PipelineNodeFactory;
import com.tradej.pipeline.service.PipelineRuntimeService;
import com.tradej.pipeline.service.reactor.ReactorBridgeMetrics;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;

import java.util.Map;

public final class PipelineComposition {

    private final VirtualClock virtualClock;
    private final ReactorBridge reactorBridge;
    private final ReactorBridgeMetrics reactorBridgeMetrics;
    private final PipelineRuntimeService pipelineRuntimeService;
    private final DagPipelineRuntimeService dagPipelineRuntimeService;
    private final DagPipelineIngressBridge dagPipelineIngressBridge;

    private PipelineComposition(
            VirtualClock virtualClock,
            ReactorBridge reactorBridge,
            ReactorBridgeMetrics reactorBridgeMetrics,
            PipelineRuntimeService pipelineRuntimeService,
            DagPipelineRuntimeService dagPipelineRuntimeService,
            DagPipelineIngressBridge dagPipelineIngressBridge
    ) {
        this.virtualClock = virtualClock;
        this.reactorBridge = reactorBridge;
        this.reactorBridgeMetrics = reactorBridgeMetrics;
        this.pipelineRuntimeService = pipelineRuntimeService;
        this.dagPipelineRuntimeService = dagPipelineRuntimeService;
        this.dagPipelineIngressBridge = dagPipelineIngressBridge;
    }

    public static PipelineComposition create(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            FeatureStore hotPathFeatureStore,
            DuckDbPipelineGraphStore pipelineGraphStore,
            ScanEngine scanEngine,
            Map<String, ScanProfile> scanProfilesById
    ) {
        return create(positionRiskHandler, candleAggregationService, graphStrategySandbox,
                executionHandler, portfolioEngine, hotPathFeatureStore,
                pipelineGraphStore, scanEngine, scanProfilesById, TradeDefaults.RUNTIME_MODE);
    }

    public static PipelineComposition create(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            FeatureStore hotPathFeatureStore,
            DuckDbPipelineGraphStore pipelineGraphStore,
            ScanEngine scanEngine,
            Map<String, ScanProfile> scanProfilesById,
            RuntimeMode runtimeMode
    ) {
        ExecutionModePolicy policy = ExecutionModePolicy.forMode(runtimeMode);
        VirtualClock.Mode clockMode = policy.usesDeterministicClock()
                ? VirtualClock.Mode.REPLAY
                : VirtualClock.Mode.LIVE;
        VirtualClock virtualClock = new VirtualClock(clockMode);
        ReactorBridge reactorBridge = new ReactorBridge();
        ReactorBridgeMetrics reactorBridgeMetrics = new ReactorBridgeMetrics();
        NodeRegistry nodeRegistry = new NodeRegistry();

        PipelineNodeFactory nodeFactory = new PipelineNodeFactory(
                nodeRegistry,
                positionRiskHandler,
                candleAggregationService,
                graphStrategySandbox,
                executionHandler,
                portfolioEngine,
                hotPathFeatureStore,
                reactorBridge,
                scanEngine,
                scanProfilesById
        );

        PipelineRuntimeService pipelineRuntimeService = new PipelineRuntimeService(
                nodeFactory, virtualClock, pipelineGraphStore, reactorBridgeMetrics, reactorBridge);

        DagPipelineRuntimeService dagPipelineRuntimeService = new DagPipelineRuntimeService(
                nodeFactory, virtualClock, pipelineGraphStore);

        DagPipelineIngressBridge dagPipelineIngressBridge = new DagPipelineIngressBridge(
                dagPipelineRuntimeService);

        return new PipelineComposition(
                virtualClock, reactorBridge, reactorBridgeMetrics,
                pipelineRuntimeService, dagPipelineRuntimeService, dagPipelineIngressBridge);
    }

    public VirtualClock virtualClock() {
        return virtualClock;
    }

    public ReactorBridge reactorBridge() {
        return reactorBridge;
    }

    public ReactorBridgeMetrics reactorBridgeMetrics() {
        return reactorBridgeMetrics;
    }

    public PipelineRuntimeService pipelineRuntimeService() {
        return pipelineRuntimeService;
    }

    public DagPipelineRuntimeService dagPipelineRuntimeService() {
        return dagPipelineRuntimeService;
    }

    public DagPipelineIngressBridge dagPipelineIngressBridge() {
        return dagPipelineIngressBridge;
    }
}
