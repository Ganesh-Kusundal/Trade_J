package com.tradej.pipeline.compiler;

import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.PipelineNodeTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes a validated {@link PipelineGraph} before compilation:
 * <ul>
 *   <li>Inserts implicit {@code Ingress} nodes for zero-in-degree nodes in DAG mode</li>
 *   <li>Infers {@code PipelineExecutionMode} from graph structure if not explicitly set</li>
 *   <li>Annotates symbol partition metadata from node config</li>
 * </ul>
 */
public final class GraphNormalizer {

    private GraphNormalizer() {
    }

    /**
     * Normalize a graph, returning a new instance if changes were needed.
     */
    public static PipelineGraph normalize(PipelineGraph raw) {
        PipelineGraph graph = raw;
        graph = inferExecutionMode(graph);
        graph = ensureIngressNodes(graph);
        return graph;
    }

    /**
     * Infer HOT_PATH vs DAG mode based on graph structure.
     * HOT_PATH if it's a simple linear chain (no branching, no merging, single entry/exit).
     * DAG if there are multiple entry points, branching (fork or merge), or scanner/ingress nodes.
     */
    static PipelineGraph inferExecutionMode(PipelineGraph graph) {
        if (graph.executionMode() != null) {
            return graph; // explicit, don't override
        }

        Set<String> presentTypes = new HashSet<>();
        for (PipelineNodeDef node : graph.nodes()) {
            presentTypes.add(node.type());
        }

        // Graphs with scanner or ingress nodes are DAG by convention
        if (presentTypes.contains(PipelineNodeTypes.SCAN)
                || presentTypes.contains(PipelineNodeTypes.INGRESS)) {
            return new PipelineGraph(graph.id(), graph.name(), graph.version(),
                    graph.nodes(), graph.edges(), PipelineExecutionMode.DAG);
        }

        if (graph.edges() == null || graph.edges().isEmpty()) {
            // No edges → single node, it's HOT_PATH
            return new PipelineGraph(graph.id(), graph.name(), graph.version(),
                    graph.nodes(), graph.edges(), PipelineExecutionMode.HOT_PATH);
        }

        // Check for branching (one source → multiple targets) OR merging (multiple sources → one target)
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        boolean hasFork = false;
        boolean hasMerge = false;

        for (PipelineEdgeDef edge : graph.edges()) {
            if (!sources.add(edge.source())) {
                hasFork = true; // Same source appears twice → fork
            }
            if (!targets.add(edge.target())) {
                hasMerge = true; // Same target appears twice → merge
            }
        }

        if (hasFork || hasMerge) {
            return new PipelineGraph(graph.id(), graph.name(), graph.version(),
                    graph.nodes(), graph.edges(), PipelineExecutionMode.DAG);
        }

        // Check for multiple entry points (more than one zero-in-degree node)
        Set<String> allTargets = new HashSet<>();
        for (PipelineEdgeDef edge : graph.edges()) {
            allTargets.add(edge.target());
        }
        long entryCount = graph.nodes().stream()
                .map(PipelineNodeDef::id)
                .filter(id -> !allTargets.contains(id))
                .count();
        if (entryCount > 1) {
            return new PipelineGraph(graph.id(), graph.name(), graph.version(),
                    graph.nodes(), graph.edges(), PipelineExecutionMode.DAG);
        }

        // Default to HOT_PATH for simple linear chains
        return new PipelineGraph(graph.id(), graph.name(), graph.version(),
                graph.nodes(), graph.edges(), PipelineExecutionMode.HOT_PATH);
    }

    /**
     * Ensure DAG graphs have at least one Ingress node.
     * If no explicit Ingress node exists, wrap zero-in-degree nodes as implicit ingress.
     */
    static PipelineGraph ensureIngressNodes(PipelineGraph graph) {
        if (graph.executionMode() != PipelineExecutionMode.DAG) {
            return graph;
        }

        boolean hasExplicitIngress = false;
        for (PipelineNodeDef node : graph.nodes()) {
            if (PipelineNodeTypes.INGRESS.equals(node.type())) {
                hasExplicitIngress = true;
                break;
            }
        }
        if (hasExplicitIngress) {
            return graph;
        }

        // Find zero-in-degree nodes to wrap as ingress
        Set<String> targetIds = new HashSet<>();
        if (graph.edges() != null) {
            for (PipelineEdgeDef edge : graph.edges()) {
                targetIds.add(edge.target());
            }
        }

        List<PipelineNodeDef> newNodes = new ArrayList<>(graph.nodes());
        List<PipelineEdgeDef> newEdges = new ArrayList<>(graph.edges() == null ? List.of() : graph.edges());
        int ingressCounter = 0;

        for (PipelineNodeDef node : graph.nodes()) {
            if (!targetIds.contains(node.id())) {
                // This is an ingress point — insert an explicit Ingress node before it
                String ingressId = "implicit-ingress-" + (++ingressCounter);
                PipelineNodeDef ingressDef = new PipelineNodeDef(
                        ingressId, PipelineNodeTypes.INGRESS, "Ingress (" + node.label() + ")",
                        Map.of("eventTypes", List.of("CandleClosed"))
                );
                newNodes.add(ingressDef);
                newEdges.add(new PipelineEdgeDef(
                        "e-ingress-" + ingressCounter, ingressId, node.id()
                ));
            }
        }

        return new PipelineGraph(graph.id(), graph.name(), graph.version(),
                List.copyOf(newNodes), List.copyOf(newEdges), graph.executionMode());
    }
}
