package com.tradej.app.pipeline;

import com.tradej.persistence.pipeline.DuckDbPipelineGraphStore;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineGraphValidator;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.graph.IngressNodeConfig;
import com.tradej.pipeline.runtime.GraphCompiler;
import com.tradej.pipeline.runtime.GraphRuntime;
import com.tradej.pipeline.runtime.NodeMetrics;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.runtime.PipelineRuntime;
import com.tradej.core.domain.runtime.RuntimeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Owns compiled DAG-mode pipeline graphs executed via ingress event routing.
 * <p>
 * Each DAG instance wraps a {@link PipelineRuntime} for unified LIVE/REPLAY/BACKTEST execution.
 */
@Service
public final class DagPipelineRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(DagPipelineRuntimeService.class);

    private final Function<PipelineNodeDef, PipelineNode> nodeFactory;
    private final VirtualClock virtualClock;
    private final DuckDbPipelineGraphStore pipelineGraphStore;
    private final ConcurrentHashMap<String, DagPipelineInstance> instances = new ConcurrentHashMap<>();

    @Autowired
    public DagPipelineRuntimeService(
            PipelineNodeFactory pipelineNodeFactory,
            VirtualClock virtualClock,
            DuckDbPipelineGraphStore pipelineGraphStore
    ) {
        this(pipelineNodeFactory::create, virtualClock, pipelineGraphStore);
    }

    DagPipelineRuntimeService(
            Function<PipelineNodeDef, PipelineNode> nodeFactory,
            VirtualClock virtualClock,
            DuckDbPipelineGraphStore pipelineGraphStore
    ) {
        this.nodeFactory = nodeFactory;
        this.virtualClock = virtualClock;
        this.pipelineGraphStore = pipelineGraphStore;
    }

    public synchronized void bootstrapGraph(PipelineGraph graph) {
        if (graph.executionMode() != PipelineExecutionMode.DAG) {
            throw new IllegalArgumentException("Graph is not a DAG pipeline: " + graph.id());
        }
        PipelineGraphValidator.validateDag(graph);
        compile(graph, false);
    }

    public synchronized void reload(PipelineGraph graph) {
        PipelineGraphValidator.validateDag(graph);
        compile(graph, true);
    }

    public synchronized void reloadFromApi(PipelineGraph graph) {
        reload(graph);
        persistGraph(graph);
    }

    public Map<String, PipelineGraph> activeGraphs() {
        Map<String, PipelineGraph> graphs = new HashMap<>();
        instances.forEach((id, instance) -> graphs.put(id, instance.graph()));
        return Map.copyOf(graphs);
    }

    public Optional<PipelineGraph> getGraph(String graphId) {
        DagPipelineInstance instance = instances.get(graphId);
        return instance == null ? Optional.empty() : Optional.of(instance.graph());
    }

    public Optional<GraphRuntime> runtime(String graphId) {
        DagPipelineInstance instance = instances.get(graphId);
        return instance == null ? Optional.empty() : Optional.of(instance.runtime());
    }

    public Optional<PipelineRuntime> pipelineRuntime(String graphId) {
        DagPipelineInstance instance = instances.get(graphId);
        return instance == null ? Optional.empty() : Optional.of(instance.pipelineRuntime());
    }

    public List<DagPipelineInstance> activeInstances() {
        return List.copyOf(instances.values());
    }

    public boolean hasActiveGraphs() {
        return !instances.isEmpty();
    }

    public Map<String, Object> metricsSnapshot() {
        Map<String, Object> payload = new HashMap<>();
        for (Map.Entry<String, DagPipelineInstance> entry : instances.entrySet()) {
            GraphRuntime runtime = entry.getValue().runtime();
            if (runtime == null) continue;
            Map<String, Object> nodeMetrics = new HashMap<>();
            for (Map.Entry<String, PipelineNode> nodeEntry : runtime.getExecutionPlan().nodesById().entrySet()) {
                PipelineNode node = nodeEntry.getValue();
                NodeMetrics metrics = node.getMetrics();
                nodeMetrics.put(nodeEntry.getKey(), Map.of(
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
            payload.put(entry.getKey(), Map.of(
                    "nodes", nodeMetrics,
                    "mode", entry.getValue().pipelineRuntime().currentMode().name()
            ));
        }
        return payload;
    }

    public List<IngressBinding> ingressBindings() {
        List<IngressBinding> bindings = new ArrayList<>();
        for (DagPipelineInstance instance : instances.values()) {
            PipelineGraph graph = instance.graph();
            GraphRuntime runtime = instance.runtime();
            for (PipelineNodeDef nodeDef : graph.nodes()) {
                if (!PipelineNodeTypes.INGRESS.equals(nodeDef.type())) {
                    continue;
                }
                bindings.add(new IngressBinding(
                        graph.id(),
                        nodeDef.id(),
                        IngressNodeConfig.fromMap(nodeDef.config()),
                        runtime,
                        instance.pipelineRuntime()
                ));
            }
        }
        return List.copyOf(bindings);
    }

    private void compile(PipelineGraph graph, boolean shutdownPrevious) {
        log.info("Compiling DAG pipeline graph id={} name={} version={}", graph.id(), graph.name(), graph.version());
        GraphCompiler compiler = new GraphCompiler(nodeFactory);
        PipelineRuntime pipelineRuntime = new PipelineRuntime(
                virtualClock,
                PipelineCompileContexts.create(virtualClock)
        );
        pipelineRuntime.deploy(graph, compiler, null);

        if (shutdownPrevious) {
            DagPipelineInstance previous = instances.get(graph.id());
            if (previous != null) {
                previous.shutdown();
            }
        }
        instances.put(graph.id(), new DagPipelineInstance(graph, pipelineRuntime));
    }

    private void persistGraph(PipelineGraph graph) {
        try {
            pipelineGraphStore.save(graph);
        } catch (Exception e) {
            log.error("Failed to persist DAG pipeline graph id={} version={}: {}", graph.id(), graph.version(), e.getMessage());
            throw new IllegalStateException("DAG pipeline graph persistence failed", e);
        }
    }

    public record IngressBinding(
            String graphId,
            String ingressNodeId,
            IngressNodeConfig config,
            GraphRuntime runtime,
            PipelineRuntime pipelineRuntime
    ) {
    }
}
