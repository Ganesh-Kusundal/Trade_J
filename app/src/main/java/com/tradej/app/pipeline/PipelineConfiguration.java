package com.tradej.app.pipeline;

import com.tradej.core.domain.port.FeatureStore;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.feature.store.OptionsAwareFeatureStore;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.reactor.ReactorBridge;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.IngressNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.service.reactor.ReactorBridgeMetrics;
import com.tradej.pipeline.service.DagPipelineRuntimeService;
import com.tradej.pipeline.service.PipelineNodeFactory;
import com.tradej.pipeline.service.PipelineRuntimeService;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.composition.config.ScanProperties;
import com.tradej.app.scanner.ScanProfileMapper;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class PipelineConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PipelineConfiguration.class);
    private static final String DEFAULT_HOT_PATH_GRAPH_ID = "hotpath-default";
    private static final List<String> DEFAULT_DAG_GRAPH_IDS = List.of("scanner-default", "scanner-tick-default");

    @Bean
    VirtualClock virtualClock() {
        return new VirtualClock(VirtualClock.Mode.LIVE);
    }

    @Bean
    CandleAggregationService candleAggregationService(TradingClock tradingClock) {
        return new CandleAggregationService(List.of("1m", "5m", "15m", "1h"), tradingClock);
    }

    @Bean
    ReactorBridge reactorBridge() {
        return new ReactorBridge();
    }

    @Bean
    ReactorBridgeMetrics reactorBridgeMetrics() {
        return new ReactorBridgeMetrics();
    }

    @Bean
    NodeRegistry nodeRegistry() {
        NodeRegistry registry = new NodeRegistry();

        // Register metadata-only descriptors for the frontend palette.
        // Factory functions are null here; PipelineNodeFactory populates them
        // with real wiring after the registry is created.
        registry.register(descriptor(PipelineNodeTypes.INGRESS, "Ingress", "io",
                "Pass-through ingress node for external event injection",
                List.of(), List.of(),
                Map.of(
                        "eventTypes", NodeTypeDescriptor.ConfigField.of("eventTypes", NodeTypeDescriptor.ConfigField.FieldType.JSON, "Accepted Event Types", List.of("CandleClosed")),
                        "intervals", NodeTypeDescriptor.ConfigField.of("intervals", NodeTypeDescriptor.ConfigField.FieldType.INTERVAL_LIST, "Filter Intervals", List.of()),
                        "symbols", NodeTypeDescriptor.ConfigField.of("symbols", NodeTypeDescriptor.ConfigField.FieldType.SYMBOL_LIST, "Filter Symbols", List.of())
                )));

        registry.register(descriptor(PipelineNodeTypes.RISK, "Pre-Trade Risk", "risk",
                "Validates pre-trade risk limits (kill switch, daily loss, max positions)",
                List.of(new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.SignalGenerated.class, "Incoming signal")),
                List.of(new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.SignalPendingExecution.class, "Approved signal")),
                Map.of()));

        registry.register(descriptor(PipelineNodeTypes.CANDLE, "Candle Aggregation", "transformation",
                "Aggregates ticks into OHLCV candles",
                List.of(
                        new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.MarketTickEvent.class, "Canonical tick"),
                        new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.MarketTickEvent.class, "Incoming tick (deprecated)")
                ),
                List.of(
                        new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.CandleDeveloping.class, "Developing candle"),
                        new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.CandleClosed.class, "Completed candle")
                ),
                Map.of()));

        registry.register(descriptor(PipelineNodeTypes.STRATEGY, "Strategy Engine", "signal",
                "Evaluates registered StrategyPlugin instances against candle closes",
                List.of(new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.CandleClosed.class, "Completed candle")),
                List.of(new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.SignalGenerated.class, "Generated signal")),
                Map.of()));

        registry.register(descriptor(PipelineNodeTypes.OMS, "Order Execution", "oms",
                "Places orders, manages OMS lifecycle, handles fills",
                List.of(new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.SignalPendingExecution.class, "Approved signal")),
                List.of(
                        new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.OrderAccepted.class, "Order accepted"),
                        new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.OrderFilled.class, "Order filled"),
                        new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.OrderRejected.class, "Order rejected")
                ),
                Map.of()));

        registry.register(descriptor(PipelineNodeTypes.REACTOR, "Reactor Cold-Path Bridge", "io",
                "Offloads cold-path events to reactive Flux subscribers",
                List.of(new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.CandleClosed.class, "Candle closed")),
                List.of(),
                Map.of()));

        registry.register(descriptor(PipelineNodeTypes.SCAN, "Scanner", "scanner",
                "Evaluates scan criteria against market data and produces scan hits",
                List.of(new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.CandleClosed.class, "Candle trigger")),
                List.of(),
                Map.of(
                        "profileId", NodeTypeDescriptor.ConfigField.of("profileId", NodeTypeDescriptor.ConfigField.FieldType.STRING, "Scan Profile", "default"),
                        "triggerInterval", NodeTypeDescriptor.ConfigField.of("triggerInterval", NodeTypeDescriptor.ConfigField.FieldType.STRING, "Trigger Interval", "5m")
                )));

        // Streaming scanner nodes
        registry.register(descriptor(PipelineNodeTypes.SCAN_CRITERION, "Scan Criterion", "scanner",
                "Evaluates a single scan criterion against incoming events",
                List.of(
                        new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.MarketTickEvent.class, "Canonical tick"),
                        new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.MarketTickEvent.class, "Tick data (deprecated)"),
                        new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.CandleClosed.class, "Completed candle")
                ),
                List.of(new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.ScanHitProduced.class, "Matched hit")),
                Map.of(
                        "criterionType", NodeTypeDescriptor.ConfigField.of("criterionType", NodeTypeDescriptor.ConfigField.FieldType.STRING, "Criterion Type", "volume-spike"),
                        "threshold", NodeTypeDescriptor.ConfigField.of("threshold", NodeTypeDescriptor.ConfigField.FieldType.NUMBER, "Match Threshold", 2.0)
                )));

        registry.register(descriptor(PipelineNodeTypes.SCAN_AGGREGATOR, "Scan Aggregator", "scanner",
                "Accumulates, deduplicates, and ranks scan hits by time window",
                List.of(new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.ScanHitProduced.class, "Criterion hits")),
                List.of(new NodeTypeDescriptor.EventType(com.tradej.core.domain.event.ScanResultsPublished.class, "Ranked results")),
                Map.of(
                        "windowMs", NodeTypeDescriptor.ConfigField.of("windowMs", NodeTypeDescriptor.ConfigField.FieldType.NUMBER, "Window (ms)", 60000L),
                        "maxHits", NodeTypeDescriptor.ConfigField.of("maxHits", NodeTypeDescriptor.ConfigField.FieldType.NUMBER, "Max Hits", 20)
                )));

        return registry;
    }

    /**
     * Creates a metadata-only descriptor with a no-op factory.
     * The factory is replaced with real wiring by {@link PipelineNodeFactory}.
     */
    private static NodeTypeDescriptor descriptor(
            String typeId, String displayName, String category, String description,
            List<NodeTypeDescriptor.EventType> inputEvents,
            List<NodeTypeDescriptor.EventType> outputEvents,
            Map<String, NodeTypeDescriptor.ConfigField> configFields
    ) {
        return new NodeTypeDescriptor(typeId, displayName, category, description,
                inputEvents, outputEvents, configFields,
                def -> { throw new UnsupportedOperationException(
                        "Node type '" + typeId + "' has no factory registered. "
                        + "Ensure PipelineNodeFactory is properly initialized."); });
    }

    @Bean
    PipelineNodeFactory pipelineNodeFactory(
            NodeRegistry nodeRegistry,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            OptionsAwareFeatureStore hotPathFeatureStore,
            ReactorBridge reactorBridge,
            @Autowired(required = false) ScanEngine scanEngine,
            @Autowired(required = false) ScanProperties scanProperties
    ) {
        FeatureStore featureStore = hotPathFeatureStore;
        Map<String, ScanProfile> scanProfilesById = (scanProperties != null ? scanProperties.profiles().stream()
                .map(ScanProfileMapper::toDomain)
                .collect(java.util.stream.Collectors.toMap(ScanProfile::id, profile -> profile, (left, right) -> right))
                : Map.of());
        return new PipelineNodeFactory(
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
    }

    @Bean
    PipelineRuntimeService pipelineRuntimeService(
            PipelineNodeFactory pipelineNodeFactory,
            VirtualClock virtualClock,
            DuckDbPipelineGraphStore pipelineGraphStore,
            ReactorBridgeMetrics reactorBridgeMetrics,
            ReactorBridge reactorBridge
    ) {
        return new PipelineRuntimeService(pipelineNodeFactory, virtualClock, pipelineGraphStore, reactorBridgeMetrics, reactorBridge);
    }

    @Bean
    ApplicationRunner pipelineGraphBootstrap(
            PipelineRuntimeService pipelineRuntimeService,
            DuckDbPipelineGraphStore pipelineGraphStore
    ) {
        return args -> {
            try {
                pipelineGraphStore.loadLatest(DEFAULT_HOT_PATH_GRAPH_ID).ifPresentOrElse(graph -> {
                    log.info("Loaded persisted pipeline graph id={} version={}", graph.id(), graph.version());
                    pipelineRuntimeService.bootstrapGraph(graph);
                }, () -> log.info("No persisted pipeline graph found — using built-in default"));
            } catch (Exception e) {
                log.warn("Failed to load persisted pipeline graph — using built-in default: {}", e.getMessage());
            }
        };
    }

    @Bean
    DagPipelineRuntimeService dagPipelineRuntimeService(
            PipelineNodeFactory pipelineNodeFactory,
            VirtualClock virtualClock,
            DuckDbPipelineGraphStore pipelineGraphStore
    ) {
        return new DagPipelineRuntimeService(pipelineNodeFactory, virtualClock, pipelineGraphStore);
    }

    @Bean
    com.tradej.pipeline.service.DagPipelineIngressBridge dagPipelineIngressBridge(
            DagPipelineRuntimeService dagPipelineRuntimeService
    ) {
        return new com.tradej.pipeline.service.DagPipelineIngressBridge(dagPipelineRuntimeService);
    }

    @Bean
    ApplicationRunner dagGraphBootstrap(
            DagPipelineRuntimeService dagPipelineRuntimeService,
            DuckDbPipelineGraphStore pipelineGraphStore
    ) {
        return args -> {
            for (String graphId : DEFAULT_DAG_GRAPH_IDS) {
                try {
                    pipelineGraphStore.loadLatest(graphId).ifPresentOrElse(graph -> {
                        if (graph.executionMode() == PipelineExecutionMode.DAG) {
                            log.info("Loaded persisted DAG graph id={} version={}", graph.id(), graph.version());
                            dagPipelineRuntimeService.bootstrapGraph(graph);
                        }
                    }, () -> log.debug("No persisted DAG graph found for id={}", graphId));
                } catch (Exception e) {
                    log.warn("Failed to load persisted DAG graph id={}: {}", graphId, e.getMessage());
                }
            }
        };
    }
}
