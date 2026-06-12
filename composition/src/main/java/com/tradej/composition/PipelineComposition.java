package com.tradej.composition;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.ScanHitProduced;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.reactor.ReactorBridge;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.registry.NodeTypeDescriptor.ConfigField;
import com.tradej.pipeline.registry.NodeTypeDescriptor.EventType;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.service.DagPipelineIngressBridge;
import com.tradej.pipeline.service.DagPipelineRuntimeService;
import com.tradej.pipeline.service.PipelineNodeFactory;
import com.tradej.pipeline.service.PipelineRuntimeService;
import com.tradej.pipeline.service.reactor.ReactorBridgeMetrics;
import com.tradej.pipeline.spi.PipelineNodeProvider;
import com.tradej.pipeline.spi.PipelineNodeRegistry;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pipeline composition root — owns the canonical pipeline runtime beans
 * (VirtualClock, ReactorBridge, NodeRegistry, PipelineNodeFactory, runtime services).
 *
 * <p>Mirrors the bean wiring that {@code app/.../app/pipeline/PipelineConfiguration.java}
 * historically performed, including the 9 hard-coded {@link NodeTypeDescriptor}s that
 * the frontend palette consumes.
 */
public final class PipelineComposition {

    private static final Logger log = LoggerFactory.getLogger(PipelineComposition.class);

    private final VirtualClock virtualClock;
    private final ReactorBridge reactorBridge;
    private final ReactorBridgeMetrics reactorBridgeMetrics;
    private final PipelineNodeRegistry pipelineNodeRegistry;
    private final NodeRegistry nodeRegistry;
    private final PipelineNodeFactory pipelineNodeFactory;
    private final PipelineRuntimeService pipelineRuntimeService;
    private final DagPipelineRuntimeService dagPipelineRuntimeService;
    private final DagPipelineIngressBridge dagPipelineIngressBridge;

    private PipelineComposition(
            VirtualClock virtualClock,
            ReactorBridge reactorBridge,
            ReactorBridgeMetrics reactorBridgeMetrics,
            PipelineNodeRegistry pipelineNodeRegistry,
            NodeRegistry nodeRegistry,
            PipelineNodeFactory pipelineNodeFactory,
            PipelineRuntimeService pipelineRuntimeService,
            DagPipelineRuntimeService dagPipelineRuntimeService,
            DagPipelineIngressBridge dagPipelineIngressBridge
    ) {
        this.virtualClock = virtualClock;
        this.reactorBridge = reactorBridge;
        this.reactorBridgeMetrics = reactorBridgeMetrics;
        this.pipelineNodeRegistry = pipelineNodeRegistry;
        this.nodeRegistry = nodeRegistry;
        this.pipelineNodeFactory = pipelineNodeFactory;
        this.pipelineRuntimeService = pipelineRuntimeService;
        this.dagPipelineRuntimeService = dagPipelineRuntimeService;
        this.dagPipelineIngressBridge = dagPipelineIngressBridge;
    }

    /**
     * Build the pipeline composition from its runtime dependencies.
     *
     * @param positionRiskHandler        pre-trade risk handler
     * @param candleAggregationService   candle aggregation service
     * @param graphStrategySandbox       graph-based strategy sandbox
     * @param executionHandler           order execution handler
     * @param portfolioEngine            portfolio engine
     * @param featureStore               hot-path feature store
     * @param pipelineGraphStore         DuckDB-backed pipeline graph store
     * @param scanEngine                 scanner engine (nullable)
     * @param scanProfilesById           map of scan profile id → profile
     * @return fully-wired {@link PipelineComposition}
     */
    public static PipelineComposition create(
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            FeatureStore featureStore,
            DuckDbPipelineGraphStore pipelineGraphStore,
            ScanEngine scanEngine,
            Map<String, ScanProfile> scanProfilesById
    ) {
        Objects.requireNonNull(positionRiskHandler, "positionRiskHandler");
        Objects.requireNonNull(candleAggregationService, "candleAggregationService");
        Objects.requireNonNull(graphStrategySandbox, "graphStrategySandbox");
        Objects.requireNonNull(executionHandler, "executionHandler");
        Objects.requireNonNull(portfolioEngine, "portfolioEngine");
        Objects.requireNonNull(featureStore, "featureStore");
        Objects.requireNonNull(pipelineGraphStore, "pipelineGraphStore");
        Objects.requireNonNull(scanProfilesById, "scanProfilesById");

        VirtualClock virtualClock = new VirtualClock(VirtualClock.Mode.LIVE);
        ReactorBridge reactorBridge = new ReactorBridge();
        ReactorBridgeMetrics reactorBridgeMetrics = new ReactorBridgeMetrics();

        PipelineNodeRegistry pipelineNodeRegistry = new PipelineNodeRegistry();
        NodeRegistry nodeRegistry = buildNodeRegistry(pipelineNodeRegistry);

        PipelineNodeFactory pipelineNodeFactory = new PipelineNodeFactory(
                nodeRegistry,
                positionRiskHandler,
                candleAggregationService,
                graphStrategySandbox,
                executionHandler,
                portfolioEngine,
                featureStore,
                reactorBridge,
                scanEngine,
                scanProfilesById
        );

        PipelineRuntimeService pipelineRuntimeService = new PipelineRuntimeService(
                pipelineNodeFactory, virtualClock, pipelineGraphStore, reactorBridgeMetrics, reactorBridge
        );

        DagPipelineRuntimeService dagPipelineRuntimeService = new DagPipelineRuntimeService(
                pipelineNodeFactory, virtualClock, pipelineGraphStore
        );

        DagPipelineIngressBridge dagPipelineIngressBridge = new DagPipelineIngressBridge(dagPipelineRuntimeService);

        log.info("PipelineComposition created (pipelineNodeProviders={}, hardcodedDescriptors=9)",
                pipelineNodeRegistry.all().size());

        return new PipelineComposition(
                virtualClock,
                reactorBridge,
                reactorBridgeMetrics,
                pipelineNodeRegistry,
                nodeRegistry,
                pipelineNodeFactory,
                pipelineRuntimeService,
                dagPipelineRuntimeService,
                dagPipelineIngressBridge
        );
    }

    /**
     * Builds the {@link NodeRegistry} with SPI-driven descriptors (via {@link PipelineNodeProvider})
     * plus the 9 hard-coded descriptors consumed by the frontend palette.
     */
    private static NodeRegistry buildNodeRegistry(PipelineNodeRegistry pipelineNodeRegistry) {
        NodeRegistry registry = new NodeRegistry();

        if (pipelineNodeRegistry != null && !pipelineNodeRegistry.all().isEmpty()) {
            for (PipelineNodeProvider provider : pipelineNodeRegistry.all()) {
                provider.registerMetadata(registry);
            }
            log.info("Registered {} node descriptors via PipelineNodeProvider SPI",
                    pipelineNodeRegistry.all().size());
        }

        registry.register(descriptor(PipelineNodeTypes.INGRESS, "Ingress", "io",
                "Pass-through ingress node for external event injection",
                List.of(), List.of(),
                Map.of(
                        "eventTypes", ConfigField.of("eventTypes", ConfigField.FieldType.JSON, "Accepted Event Types", List.of("CandleClosed")),
                        "intervals", ConfigField.of("intervals", ConfigField.FieldType.INTERVAL_LIST, "Filter Intervals", List.of()),
                        "symbols", ConfigField.of("symbols", ConfigField.FieldType.SYMBOL_LIST, "Filter Symbols", List.of())
                )));

        registry.register(descriptor(PipelineNodeTypes.RISK, "Pre-Trade Risk", "risk",
                "Validates pre-trade risk limits (kill switch, daily loss, max positions)",
                List.of(new EventType(SignalGenerated.class, "Incoming signal")),
                List.of(new EventType(SignalPendingExecution.class, "Approved signal")),
                Map.of()));

        registry.register(descriptor(PipelineNodeTypes.CANDLE, "Candle Aggregation", "transformation",
                "Aggregates ticks into OHLCV candles",
                List.of(
                        new EventType(MarketTickEvent.class, "Canonical tick"),
                        new EventType(MarketTickEvent.class, "Incoming tick (deprecated)")
                ),
                List.of(
                        new EventType(CandleDeveloping.class, "Developing candle"),
                        new EventType(CandleClosed.class, "Completed candle")
                ),
                Map.of()));

        registry.register(descriptor(PipelineNodeTypes.STRATEGY, "Strategy Engine", "signal",
                "Evaluates registered StrategyPlugin instances against candle closes",
                List.of(new EventType(CandleClosed.class, "Completed candle")),
                List.of(new EventType(SignalGenerated.class, "Generated signal")),
                Map.of()));

        registry.register(descriptor(PipelineNodeTypes.OMS, "Order Execution", "oms",
                "Places orders, manages OMS lifecycle, handles fills",
                List.of(new EventType(SignalPendingExecution.class, "Approved signal")),
                List.of(
                        new EventType(OrderAccepted.class, "Order accepted"),
                        new EventType(OrderFilled.class, "Order filled"),
                        new EventType(OrderRejected.class, "Order rejected")
                ),
                Map.of()));

        registry.register(descriptor(PipelineNodeTypes.REACTOR, "Reactor Cold-Path Bridge", "io",
                "Offloads cold-path events to reactive Flux subscribers",
                List.of(new EventType(CandleClosed.class, "Candle closed")),
                List.of(),
                Map.of()));

        registry.register(descriptor(PipelineNodeTypes.SCAN, "Scanner", "scanner",
                "Evaluates scan criteria against market data and produces scan hits",
                List.of(new EventType(CandleClosed.class, "Candle trigger")),
                List.of(),
                Map.of(
                        "profileId", ConfigField.of("profileId", ConfigField.FieldType.STRING, "Scan Profile", "default"),
                        "triggerInterval", ConfigField.of("triggerInterval", ConfigField.FieldType.STRING, "Trigger Interval", "5m")
                )));

        registry.register(descriptor(PipelineNodeTypes.SCAN_CRITERION, "Scan Criterion", "scanner",
                "Evaluates a single scan criterion against incoming events",
                List.of(
                        new EventType(MarketTickEvent.class, "Canonical tick"),
                        new EventType(MarketTickEvent.class, "Tick data (deprecated)"),
                        new EventType(CandleClosed.class, "Completed candle")
                ),
                List.of(new EventType(ScanHitProduced.class, "Matched hit")),
                Map.of(
                        "criterionType", ConfigField.of("criterionType", ConfigField.FieldType.STRING, "Criterion Type", "volume-spike"),
                        "threshold", ConfigField.of("threshold", ConfigField.FieldType.NUMBER, "Match Threshold", 2.0)
                )));

        registry.register(descriptor(PipelineNodeTypes.SCAN_AGGREGATOR, "Scan Aggregator", "scanner",
                "Accumulates, deduplicates, and ranks scan hits by time window",
                List.of(new EventType(ScanHitProduced.class, "Criterion hits")),
                List.of(new EventType(ScanResultsPublished.class, "Ranked results")),
                Map.of(
                        "windowMs", ConfigField.of("windowMs", ConfigField.FieldType.NUMBER, "Window (ms)", 60000L),
                        "maxHits", ConfigField.of("maxHits", ConfigField.FieldType.NUMBER, "Max Hits", 20)
                )));

        return registry;
    }

    /**
     * Creates a metadata-only descriptor with a no-op factory. The factory is replaced with
     * real wiring by {@link PipelineNodeFactory}.
     */
    private static NodeTypeDescriptor descriptor(
            String typeId, String displayName, String category, String description,
            List<EventType> inputEvents,
            List<EventType> outputEvents,
            Map<String, ConfigField> configFields
    ) {
        return new NodeTypeDescriptor(typeId, displayName, category, description,
                inputEvents, outputEvents, configFields,
                def -> {
                    throw new UnsupportedOperationException(
                            "Node type '" + typeId + "' has no factory registered. "
                                    + "Ensure PipelineNodeFactory is properly initialized.");
                });
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

    public PipelineNodeRegistry pipelineNodeRegistry() {
        return pipelineNodeRegistry;
    }

    public NodeRegistry nodeRegistry() {
        return nodeRegistry;
    }

    public PipelineNodeFactory pipelineNodeFactory() {
        return pipelineNodeFactory;
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
