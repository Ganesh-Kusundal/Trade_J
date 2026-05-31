package com.tradej.pipeline.graph;

import com.tradej.pipeline.runtime.PipelineNodeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates declarative pipeline graphs before compilation.
 */
public final class PipelineGraphValidator {

    private static final Logger log = LoggerFactory.getLogger(PipelineGraphValidator.class);

    private static final Set<String> KNOWN_NODE_TYPES = Set.of(
            PipelineNodeTypes.INGRESS,
            PipelineNodeTypes.RISK,
            PipelineNodeTypes.CANDLE,
            PipelineNodeTypes.FEATURE,
            PipelineNodeTypes.STRATEGY,
            PipelineNodeTypes.PORTFOLIO,
            PipelineNodeTypes.OMS,
            PipelineNodeTypes.REACTOR,
            PipelineNodeTypes.SCAN,
            // Decomposed execution nodes
            PipelineNodeTypes.SIGNAL_GATE,
            PipelineNodeTypes.ORDER_PLACEMENT,
            PipelineNodeTypes.FILL_RECONCILIATION,
            // Streaming scanner nodes
            PipelineNodeTypes.SCAN_CRITERION,
            PipelineNodeTypes.SCAN_AGGREGATOR
    );

    private static final Set<String> HOT_PATH_REQUIRED_TYPES = Set.of(
            PipelineNodeTypes.RISK,
            PipelineNodeTypes.CANDLE,
            PipelineNodeTypes.STRATEGY,
            PipelineNodeTypes.OMS
    );

    private PipelineGraphValidator() {
    }

    public static void validate(PipelineGraph graph) {
        if (graph.executionMode() == PipelineExecutionMode.DAG) {
            validateDag(graph);
        } else {
            validateHotPath(graph);
        }
    }

    public static void validate(PipelineGraph graph, boolean hotPath) {
        if (hotPath) {
            validateHotPath(graph);
        } else {
            validateDag(graph);
        }
    }

    public static void validateHotPath(PipelineGraph graph) {
        validateStructure(graph);
        Set<String> presentTypes = presentTypes(graph);
        List<String> missing = HOT_PATH_REQUIRED_TYPES.stream()
                .filter(type -> !presentTypes.contains(type))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Hot-path graph missing required node types: " + missing);
        }
    }

    public static void validateDag(PipelineGraph graph) {
        validateStructure(graph);
        Set<String> presentTypes = presentTypes(graph);
        if (presentTypes.contains(PipelineNodeTypes.OMS)) {
            throw new IllegalArgumentException("DAG graph must not contain OMS nodes");
        }
        if (presentTypes.contains(PipelineNodeTypes.RISK)) {
            log.warn("DAG graph id={} contains Risk node — unusual for non-hot-path execution", graph.id());
        }
        if (!hasIngressEntryPoint(graph)) {
            throw new IllegalArgumentException("DAG graph must contain at least one Ingress node or zero-in-degree entry node");
        }
    }

    private static void validateStructure(PipelineGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Pipeline graph cannot be null");
        }
        if (graph.id() == null || graph.id().isBlank()) {
            throw new IllegalArgumentException("Pipeline graph id is required");
        }
        if (graph.nodes() == null || graph.nodes().isEmpty()) {
            throw new IllegalArgumentException("Pipeline graph must contain at least one node");
        }

        Set<String> nodeIds = new HashSet<>();
        for (PipelineNodeDef node : graph.nodes()) {
            if (node.id() == null || node.id().isBlank()) {
                throw new IllegalArgumentException("Pipeline node id is required");
            }
            if (!nodeIds.add(node.id())) {
                throw new IllegalArgumentException("Duplicate pipeline node id: " + node.id());
            }
            if (node.type() == null || node.type().isBlank()) {
                throw new IllegalArgumentException("Pipeline node type is required for id=" + node.id());
            }
            if (!KNOWN_NODE_TYPES.contains(node.type())) {
                throw new IllegalArgumentException("Unknown pipeline node type '" + node.type() + "' for id=" + node.id());
            }
            if (PipelineNodeTypes.SCAN.equals(node.type())) {
                Object profileId = node.config() == null ? null : node.config().get("profileId");
                if (!(profileId instanceof String s) || s.isBlank()) {
                    throw new IllegalArgumentException("Scan node id=" + node.id() + " requires config.profileId");
                }
            }
        }

        if (graph.edges() != null) {
            for (PipelineEdgeDef edge : graph.edges()) {
                if (edge.source() == null || edge.source().isBlank()) {
                    throw new IllegalArgumentException("Pipeline edge source is required");
                }
                if (edge.target() == null || edge.target().isBlank()) {
                    throw new IllegalArgumentException("Pipeline edge target is required");
                }
                if (!nodeIds.contains(edge.source())) {
                    throw new IllegalArgumentException("Pipeline edge references unknown source node: " + edge.source());
                }
                if (!nodeIds.contains(edge.target())) {
                    throw new IllegalArgumentException("Pipeline edge references unknown target node: " + edge.target());
                }
                if (edge.source().equals(edge.target())) {
                    throw new IllegalArgumentException("Pipeline edge cannot connect a node to itself: " + edge.source());
                }
            }
        }
    }

    private static Set<String> presentTypes(PipelineGraph graph) {
        Set<String> presentTypes = new HashSet<>();
        for (PipelineNodeDef node : graph.nodes()) {
            presentTypes.add(node.type());
        }
        return presentTypes;
    }

    private static boolean hasIngressEntryPoint(PipelineGraph graph) {
        Set<String> targetIds = new HashSet<>();
        if (graph.edges() != null) {
            for (PipelineEdgeDef edge : graph.edges()) {
                targetIds.add(edge.target());
            }
        }
        for (PipelineNodeDef node : graph.nodes()) {
            if (PipelineNodeTypes.INGRESS.equals(node.type())) {
                return true;
            }
            if (!targetIds.contains(node.id())) {
                return true;
            }
        }
        return false;
    }
}
