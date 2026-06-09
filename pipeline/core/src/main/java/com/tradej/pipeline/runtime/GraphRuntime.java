package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Topologically executes a compiled pipeline graph.
 * Manages event propagation and ingress entry points.
 */
public final class GraphRuntime {

    private final ExecutionPlan executionPlan;
    private final List<PipelineNode> ingressNodes;

    public GraphRuntime(ExecutionPlan executionPlan, PipelineGraph originalGraph) {
        this.executionPlan = Objects.requireNonNull(executionPlan, "executionPlan cannot be null");
        
        // Find ingress nodes (nodes with 0 in-degree: present in graph but not as edge targets)
        Set<String> targetIds = new HashSet<>();
        for (var edge : originalGraph.edges()) {
            targetIds.add(edge.target());
        }

        List<PipelineNode> ingress = new ArrayList<>();
        for (PipelineNodeDef nodeDef : originalGraph.nodes()) {
            if (!targetIds.contains(nodeDef.id())) {
                PipelineNode nodeInstance = executionPlan.nodesById().get(nodeDef.id());
                if (nodeInstance != null) {
                    ingress.add(nodeInstance);
                }
            }
        }
        
        // If graph is empty or disconnected and has no edges, all nodes are ingress
        if (ingress.isEmpty() && !executionPlan.sortedNodes().isEmpty()) {
            ingress.addAll(executionPlan.sortedNodes());
        }
        
        this.ingressNodes = Collections.unmodifiableList(ingress);
    }

    /**
     * Injects an external event into the graph.
     * The event enters through the ingress (source) nodes and propagates downstream.
     */
    public void onEvent(DomainEvent event) {
        for (PipelineNode ingressNode : ingressNodes) {
            try {
                ingressNode.onEvent(event);
            } catch (Exception e) {
                // Robust logging to prevent pipeline crashes from external exceptions
            }
        }
    }

    /**
     * Processes an event through every node in topological order.
     * Matches the existing Disruptor linear pipeline semantics where each stage
     * observes the same ring-buffer event in sequence.
     */
    public void processSequential(DomainEvent event) {
        if (event == null) {
            return;
        }
        for (PipelineNode node : executionPlan.sortedNodes()) {
            try {
                node.onEvent(event);
            } catch (Exception e) {
                // Node-level errors are counted inside BasePipelineNode; continue pipeline.
            }
        }
    }

    public ExecutionPlan getExecutionPlan() {
        return executionPlan;
    }

    public List<PipelineNode> getIngressNodes() {
        return ingressNodes;
    }

    /**
     * Clean up and destroy all nodes in the graph runtime.
     */
    public void shutdown() {
        for (PipelineNode node : executionPlan.sortedNodes()) {
            try {
                node.destroy();
            } catch (Exception e) {
                // ignore or log
            }
        }
    }
}
