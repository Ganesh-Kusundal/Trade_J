package com.tradej.pipeline.service;

import com.tradej.core.domain.port.FeatureStore;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.execution.node.OmsNode;
import com.tradej.execution.node.RiskNode;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.feature.store.node.FeatureNode;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.reactor.ReactorBridge;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.pipeline.runtime.IngressNode;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.criterion.ScanCriterionRegistry;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.ScanAsset;
import com.tradej.scanner.model.ScanContext;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.scanner.node.*;
import com.tradej.strategy.node.CandleNode;
import com.tradej.strategy.node.PortfolioNode;
import com.tradej.strategy.node.StrategyNode;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.GraphStrategySandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PipelineNodeFactory {

    private static final Logger log = LoggerFactory.getLogger(PipelineNodeFactory.class);

    private final NodeRegistry nodeRegistry;
    private final PositionRiskHandler positionRiskHandler;
    private final CandleAggregationService candleAggregationService;
    private final GraphStrategySandbox graphStrategySandbox;
    private final ExecutionHandler executionHandler;
    private final PortfolioEngine portfolioEngine;
    private final FeatureStore hotPathFeatureStore;
    private final ReactorBridge reactorBridge;
    private final ScanEngine scanEngine;
    private final Map<String, ScanProfile> scanProfilesById;
    private final ScanCriterionRegistry criterionRegistry;

    public PipelineNodeFactory(
            NodeRegistry nodeRegistry,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            FeatureStore hotPathFeatureStore,
            ReactorBridge reactorBridge,
            ScanEngine scanEngine,
            Map<String, ScanProfile> scanProfilesById
    ) {
        this(nodeRegistry, positionRiskHandler, candleAggregationService, null,
                executionHandler, portfolioEngine, hotPathFeatureStore, reactorBridge, scanEngine, scanProfilesById);
    }

    public PipelineNodeFactory(
            NodeRegistry nodeRegistry,
            PositionRiskHandler positionRiskHandler,
            CandleAggregationService candleAggregationService,
            GraphStrategySandbox graphStrategySandbox,
            ExecutionHandler executionHandler,
            PortfolioEngine portfolioEngine,
            FeatureStore hotPathFeatureStore,
            ReactorBridge reactorBridge,
            ScanEngine scanEngine,
            Map<String, ScanProfile> scanProfilesById
    ) {
        this.nodeRegistry = Objects.requireNonNull(nodeRegistry, "nodeRegistry");
        this.positionRiskHandler = Objects.requireNonNull(positionRiskHandler);
        this.candleAggregationService = Objects.requireNonNull(candleAggregationService);
        this.graphStrategySandbox = graphStrategySandbox;
        this.executionHandler = Objects.requireNonNull(executionHandler);
        this.portfolioEngine = Objects.requireNonNull(portfolioEngine);
        this.hotPathFeatureStore = hotPathFeatureStore;
        this.reactorBridge = Objects.requireNonNull(reactorBridge);
        this.scanEngine = scanEngine;
        this.scanProfilesById = scanProfilesById == null ? Map.of() : Map.copyOf(scanProfilesById);
        this.criterionRegistry = new ScanCriterionRegistry();
        registerBuiltinCriteria();
        registerNodeFactories();
    }

    private void registerNodeFactories() {
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.INGRESS, def -> new IngressNode()));
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.RISK, def -> new RiskNode(positionRiskHandler)));
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.CANDLE, def -> new CandleNode(candleAggregationService)));
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.FEATURE, def -> hotPathFeatureStore != null
                ? new FeatureNode(hotPathFeatureStore)
                : noopNode("Feature store unavailable")));
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.STRATEGY, def -> new StrategyNode(graphStrategySandbox)));
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.PORTFOLIO, def -> new PortfolioNode(portfolioEngine)));
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.OMS, def -> new OmsNode(executionHandler)));
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.REACTOR, def -> reactorBridge));
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.SCAN, def -> scanEngine != null
                ? new ScanNode(scanEngine, scanProfilesById)
                : noopNode("Scan engine unavailable")));
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.SCAN_CRITERION, def -> {
            String criterionType = stringConfig(def, "criterionType", "volume-spike");
            ScanCriterion criterion = resolveCriterion(criterionType);
            return new StreamingScanCriterionNode(criterion, dummyAsset(def), stringConfig(def, "profileId", "default"));
        }));
        nodeRegistry.register(factoryDescriptor(PipelineNodeTypes.SCAN_AGGREGATOR, def -> new ScanAggregatorNode(
                stringConfig(def, "profileId", "default"),
                longConfig(def, "windowMs", 60000L),
                (int) longConfig(def, "maxHits", 20))));
        log.info("Registered {} node factories in NodeRegistry", nodeRegistry.size());
    }

    private NodeTypeDescriptor factoryDescriptor(String typeId, java.util.function.Function<PipelineNodeDef, PipelineNode> factory) {
        NodeTypeDescriptor existing = nodeRegistry.all().get(typeId);
        return new NodeTypeDescriptor(
                existing != null ? existing.typeId() : typeId,
                existing != null ? existing.displayName() : typeId,
                existing != null ? existing.category() : "unknown",
                existing != null ? existing.description() : "",
                existing != null ? existing.inputEvents() : List.of(),
                existing != null ? existing.outputEvents() : List.of(),
                existing != null ? existing.configFields() : Map.of(),
                factory
        );
    }

    public PipelineNode create(PipelineNodeDef definition) {
        return switch (definition.type()) {
            case PipelineNodeTypes.INGRESS -> new IngressNode();
            case PipelineNodeTypes.RISK -> new RiskNode(positionRiskHandler);
            case PipelineNodeTypes.CANDLE -> new CandleNode(candleAggregationService);
            case PipelineNodeTypes.FEATURE -> {
                if (hotPathFeatureStore == null) {
                    log.warn("Feature node requested but no hot-path feature store configured — using no-op node id={}",
                            definition.id());
                    yield noopNode("Feature store unavailable");
                }
                yield new FeatureNode(hotPathFeatureStore);
            }
            case PipelineNodeTypes.STRATEGY -> new StrategyNode(graphStrategySandbox);
            case PipelineNodeTypes.PORTFOLIO -> new PortfolioNode(portfolioEngine);
            case PipelineNodeTypes.OMS -> new OmsNode(executionHandler);
            case PipelineNodeTypes.REACTOR -> reactorBridge;
            case PipelineNodeTypes.SCAN -> {
                if (scanEngine == null) {
                    yield noopNode("Scan engine unavailable");
                }
                yield new ScanNode(scanEngine, scanProfilesById);
            }
            case PipelineNodeTypes.SCAN_CRITERION -> {
                String criterionType = stringConfig(definition, "criterionType", "volume-spike");
                ScanCriterion criterion = resolveCriterion(criterionType);
                yield new StreamingScanCriterionNode(criterion, dummyAsset(definition), stringConfig(definition, "profileId", "default"));
            }
            case PipelineNodeTypes.SCAN_AGGREGATOR -> new ScanAggregatorNode(
                    stringConfig(definition, "profileId", "default"),
                    longConfig(definition, "windowMs", 60000L),
                    (int) longConfig(definition, "maxHits", 20));
            default -> {
                log.warn("Unknown pipeline node type '{}' for id={} — using no-op node", definition.type(), definition.id());
                yield noopNode("Unknown node type: " + definition.type());
            }
        };
    }

    private static String stringConfig(PipelineNodeDef def, String key, String defaultValue) {
        if (def.config() == null) return defaultValue;
        Object v = def.config().get(key);
        return v instanceof String s && !s.isBlank() ? s : defaultValue;
    }

    private static long longConfig(PipelineNodeDef def, String key, long defaultValue) {
        if (def.config() == null) return defaultValue;
        Object v = def.config().get(key);
        return v instanceof Number n ? n.longValue() : defaultValue;
    }

    private ScanCriterion resolveCriterion(String type) {
        return criterionRegistry.get(type).orElseGet(() -> {
            log.warn("Scan criterion '{}' not registered — using no-op criterion", type);
            return new ScanCriterion() {
                @Override public String type() { return type; }
                @Override public boolean matches(ScanContext ctx) { return false; }
                @Override public double score(ScanContext ctx) { return 0; }
            };
        });
    }

    private void registerBuiltinCriteria() {
        try {
            var spike = new com.tradej.scanner.criterion.VolumeSpikeCriterion(2.0, 1000);
            criterionRegistry.register(spike);
        } catch (Exception e) {
            log.warn("Failed to register VolumeSpikeCriterion: {}", e.getMessage());
        }
    }

    private static ScanAsset dummyAsset(PipelineNodeDef def) {
        String symbol = stringConfig(def, "symbol", "");
        var exchangeSegment = com.tradej.core.domain.value.ExchangeSegment.NSE_EQ;
        var instrument = new com.tradej.core.domain.model.Instrument(
                symbol, symbol,
                com.tradej.core.domain.value.Exchange.NSE,
                exchangeSegment,
                "EQUITY", symbol, null, 0L, null, 1, 0
        );
        return new ScanAsset(instrument, com.tradej.core.domain.scan.AssetClass.EQUITY, symbol);
    }

    public ReactorBridge reactorBridge() {
        return reactorBridge;
    }

    private static PipelineNode noopNode(String reason) {
        return new BasePipelineNode() {
            @Override
            protected void onInit() {
            }

            @Override
            protected void processEvent(com.tradej.core.domain.event.DomainEvent event) {
            }

            @Override
            protected void onError(com.tradej.core.domain.event.DomainEvent event, Throwable t) {
            }
        };
    }
}
