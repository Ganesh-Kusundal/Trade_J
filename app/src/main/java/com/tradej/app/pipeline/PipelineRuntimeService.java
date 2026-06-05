package com.tradej.app.pipeline;

import com.tradej.app.pipeline.reactor.ReactorBridgeMetrics;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineGraphValidator;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.reactor.ReactorBridge;
import com.tradej.pipeline.runtime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Owns the live compiled pipeline graph used by the Disruptor hot path.
 * <p>
 * Uses {@link PipelineRuntime} internally to abstract LIVE/REPLAY/BACKTEST modes.
 */
@Service
public final class PipelineRuntimeService implements PipelineRuntimeBridge {

    private static final Logger log = LoggerFactory.getLogger(PipelineRuntimeService.class);

    private final PipelineNodeFactory nodeFactory;
    private final VirtualClock virtualClock;
    private final DuckDbPipelineGraphStore pipelineGraphStore;
    private final ReactorBridgeMetrics reactorBridgeMetrics;
    private final ReactorBridge reactorBridge;
    private final PipelineRuntime pipelineRuntime;
    private volatile PipelineGraph activeGraph;
    private volatile Consumer<DomainEvent> hotPathPublisher;

    public PipelineRuntimeService(
            PipelineNodeFactory nodeFactory,
            VirtualClock virtualClock,
            DuckDbPipelineGraphStore pipelineGraphStore,
            ReactorBridgeMetrics reactorBridgeMetrics,
            ReactorBridge reactorBridge
    ) {
        this.nodeFactory = nodeFactory;
        this.virtualClock = virtualClock;
        this.pipelineGraphStore = pipelineGraphStore;
        this.reactorBridgeMetrics = reactorBridgeMetrics;
        this.reactorBridge = reactorBridge;
        this.activeGraph = defaultHotPathGraph();
        this.pipelineRuntime = new PipelineRuntime(
                virtualClock,
                PipelineCompileContexts.create(virtualClock)
        );
    }

    public void bootstrapGraph(PipelineGraph graph) {
        this.activeGraph = graph;
    }

    @Override
    public void compileHotPath(Consumer<DomainEvent> hotPathPublisher) {
        this.hotPathPublisher = hotPathPublisher;
        reload(activeGraph, hotPathPublisher);
    }

    @Override
    public synchronized void reload(PipelineGraph graph, Consumer<DomainEvent> hotPathPublisher) {
        if (graph.executionMode() == PipelineExecutionMode.DAG) {
            throw new IllegalArgumentException("DAG graphs must be deployed via DagPipelineRuntimeService: " + graph.id());
        }
        PipelineGraphValidator.validateHotPath(graph);
        log.info("Compiling pipeline graph id={} name={} version={} hotPath={}",
                graph.id(), graph.name(), graph.version(), hotPathPublisher != null);

        GraphCompiler compiler = new GraphCompiler(nodeFactory::create);
        pipelineRuntime.deploy(graph, compiler, hotPathPublisher);
        this.activeGraph = graph;

        // Wire reactive sources into the ReactorBridge
        wireReactiveSources();
    }

    /**
     * Scans the active execution plan for nodes implementing {@link ReactivePipelineNode}
     * and registers them with the {@link ReactorBridge} for cold-path event streaming.
     * Clears previously registered sources first to prevent duplicates on reload.
     */
    private void wireReactiveSources() {
        ExecutionPlan plan = pipelineRuntime.executionPlan();
        if (plan == null) return;
        // Clear existing sources (they were created in the old runtime which is now shut down)
        reactorBridge.clearSources();
        int registered = 0;
        for (Map.Entry<String, PipelineNode> entry : plan.nodesById().entrySet()) {
            PipelineNode node = entry.getValue();
            if (node instanceof ReactivePipelineNode rpn && node != reactorBridge) {
                reactorBridge.registerSource(rpn);
                registered++;
            }
        }
        if (registered > 0) {
            log.info("Wired {} reactive node sources into ReactorBridge", registered);
        }
    }

    @Override
    public AtomicReference<GraphRuntime> runtimeRef() {
        return pipelineRuntime.runtimeRef();
    }

    @Override
    public PipelineGraph activeGraph() {
        return activeGraph;
    }

    public GraphRuntime runtime() {
        return pipelineRuntime.active();
    }

    public PipelineRuntime pipelineRuntime() {
        return pipelineRuntime;
    }

    public void switchMode(RuntimeMode mode) {
        pipelineRuntime.switchMode(mode);
    }

    public Map<String, Object> metricsSnapshot() {
        ExecutionPlan plan = pipelineRuntime.executionPlan();
        if (plan == null || plan.nodesById().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> payload = new HashMap<>();
        for (Map.Entry<String, PipelineNode> entry : plan.nodesById().entrySet()) {
            PipelineNode node = entry.getValue();
            NodeMetrics metrics = node.getMetrics();
            payload.put(entry.getKey(), Map.of(
                    "state", node.getState().name(),
                    "metrics", Map.of(
                            "processedCount", metrics.processedCount(),
                            "errorCount", metrics.errorCount(),
                            "lastProcessedTimestampMs", metrics.lastProcessedTimestampMs(),
                            "lastExecutionNs", metrics.lastExecutionNs(),
                            "averageExecutionNs", metrics.averageExecutionNs()
                    )
            ));
        }
        payload.put("reactor", reactorBridgeMetrics.snapshot());
        payload.put("mode", pipelineRuntime.currentMode().name());
        return payload;
    }

    public synchronized void reloadFromApi(PipelineGraph graph) {
        com.tradej.pipeline.graph.PipelineGraphValidator.validate(graph);
        reload(graph, hotPathPublisher);
        persistGraph(graph);
    }

    public synchronized void persistActiveGraph() {
        persistGraph(activeGraph);
    }

    private void persistGraph(PipelineGraph graph) {
        try {
            pipelineGraphStore.save(graph);
        } catch (Exception e) {
            log.error("Failed to persist pipeline graph id={} version={}: {}", graph.id(), graph.version(), e.getMessage());
            throw new IllegalStateException("Pipeline graph persistence failed", e);
        }
    }

    public java.util.List<DuckDbPipelineGraphStore.PipelineGraphVersion> listGraphVersions(String graphId) {
        try {
            return pipelineGraphStore.listVersions(graphId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list pipeline graph versions", e);
        }
    }

    public PipelineGraph loadGraphVersion(String graphId, int version) {
        try {
            return pipelineGraphStore.loadVersion(graphId, version)
                    .orElseThrow(() -> new IllegalArgumentException("Graph version not found: " + graphId + " v" + version));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load pipeline graph version", e);
        }
    }

    // ── Template Graphs ───────────────────────────────────────────────────

    public static PipelineGraph defaultHotPathGraph() {
        List<PipelineNodeDef> nodes = List.of(
                new PipelineNodeDef("risk-1", PipelineNodeTypes.RISK, "Pre-Trade Risk", Map.of()),
                new PipelineNodeDef("candle-1", PipelineNodeTypes.CANDLE, "Candle Aggregation", Map.of()),
                new PipelineNodeDef("feature-1", PipelineNodeTypes.FEATURE, "Hot-Path Feature Sync", Map.of()),
                new PipelineNodeDef("strategy-1", PipelineNodeTypes.STRATEGY, "Strategy Engine", Map.of()),
                new PipelineNodeDef("execution-1", PipelineNodeTypes.OMS, "Order Execution", Map.of()),
                new PipelineNodeDef("reactor-1", PipelineNodeTypes.REACTOR, "Reactor Cold-Path Bridge", Map.of())
        );

        List<PipelineEdgeDef> edges = List.of(
                new PipelineEdgeDef("e1", "risk-1", "candle-1"),
                new PipelineEdgeDef("e2", "candle-1", "feature-1"),
                new PipelineEdgeDef("e3", "feature-1", "strategy-1"),
                new PipelineEdgeDef("e4", "strategy-1", "execution-1"),
                new PipelineEdgeDef("e5", "execution-1", "reactor-1")
        );

        return new PipelineGraph(
                "hotpath-default",
                "Live Hot-Path Pipeline",
                1,
                nodes,
                edges,
                PipelineExecutionMode.HOT_PATH
        );
    }

    public static PipelineGraph defaultScannerGraph(String profileId) {
        String resolvedProfileId = profileId == null || profileId.isBlank() ? "default" : profileId;
        List<PipelineNodeDef> nodes = List.of(
                new PipelineNodeDef("ingress-1", PipelineNodeTypes.INGRESS, "Market Ingress", Map.of(
                        "eventTypes", List.of("CandleClosed"),
                        "intervals", List.of("5m"),
                        "symbols", List.of()
                )),
                new PipelineNodeDef("scan-1", PipelineNodeTypes.SCAN, "Batch Scanner", Map.of(
                        "profileId", resolvedProfileId,
                        "triggerInterval", "5m",
                        "minIntervalMs", 60_000L
                )),
                new PipelineNodeDef("reactor-1", PipelineNodeTypes.REACTOR, "Reactor Cold-Path Bridge", Map.of())
        );
        List<PipelineEdgeDef> edges = List.of(
                new PipelineEdgeDef("e1", "ingress-1", "scan-1"),
                new PipelineEdgeDef("e2", "scan-1", "reactor-1")
        );
        return new PipelineGraph(
                "scanner-default",
                "Scanner Pipeline",
                1,
                nodes,
                edges,
                PipelineExecutionMode.DAG
        );
    }

    public static PipelineGraph defaultScannerTickGraph(String profileId) {
        String resolvedProfileId = profileId == null || profileId.isBlank() ? "default" : profileId;
        List<PipelineNodeDef> nodes = List.of(
                new PipelineNodeDef("ingress-1", PipelineNodeTypes.INGRESS, "Tick Ingress", Map.of(
                        "eventTypes", List.of("MarketTickEvent"),
                        "symbols", List.of()
                )),
                new PipelineNodeDef("candle-1", PipelineNodeTypes.CANDLE, "Candle Aggregation", Map.of()),
                new PipelineNodeDef("scan-1", PipelineNodeTypes.SCAN, "Batch Scanner", Map.of(
                        "profileId", resolvedProfileId,
                        "triggerInterval", "5m",
                        "minIntervalMs", 60_000L
                )),
                new PipelineNodeDef("reactor-1", PipelineNodeTypes.REACTOR, "Reactor Cold-Path Bridge", Map.of())
        );
        List<PipelineEdgeDef> edges = List.of(
                new PipelineEdgeDef("e1", "ingress-1", "candle-1"),
                new PipelineEdgeDef("e2", "candle-1", "scan-1"),
                new PipelineEdgeDef("e3", "scan-1", "reactor-1")
        );
        return new PipelineGraph(
                "scanner-tick-default",
                "Scanner Tick Pipeline",
                1,
                nodes,
                edges,
                PipelineExecutionMode.DAG
        );
    }

    public static Map<String, PipelineGraph> graphTemplates(String defaultScanProfileId) {
        Map<String, PipelineGraph> templates = new HashMap<>();
        templates.put("hotpath-default", defaultHotPathGraph());
        templates.put("scanner-default", defaultScannerGraph(defaultScanProfileId));
        templates.put("scanner-tick-default", defaultScannerTickGraph(defaultScanProfileId));
        return Map.copyOf(templates);
    }
}
