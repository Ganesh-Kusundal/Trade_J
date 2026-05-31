package com.tradej.pipeline.runtime;

import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.core.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Compiles a declarative PipelineGraph into a compiled, runnable topological ExecutionPlan.
 * Performs cycle detection using Kahn's algorithm.
 * <p>
 * Supports partition-aware node instantiation via {@link PartitionedNode}: when a node's
 * config contains a {@code partitions} key, the compiler wraps the factory in a
 * {@code PartitionedNode} with that many shards.
 */
public final class GraphCompiler {

    private final Function<PipelineNodeDef, PipelineNode> nodeFactory;
    /** Default number of partitions when not specified per-node. 0 means no partitioning. */
    private final int defaultPartitions;

    public GraphCompiler(Function<PipelineNodeDef, PipelineNode> nodeFactory) {
        this(nodeFactory, 0);
    }

    public GraphCompiler(Function<PipelineNodeDef, PipelineNode> nodeFactory, int defaultPartitions) {
        this.nodeFactory = Objects.requireNonNull(nodeFactory, "nodeFactory cannot be null");
        this.defaultPartitions = Math.max(0, defaultPartitions);
    }

    public ExecutionPlan compile(PipelineGraph graph, PipelineContext context) {
        return compile(graph, context, null);
    }

    /**
     * Compiles a graph for hot-path execution.
     *
     * @param hotPathPublisher when non-null, all node {@code context.publish()} calls route
     *                         to this publisher (Disruptor downstream queue semantics).
     *                         When null, publish routes follow graph edge adjacency (DAG mode).
     */
    public ExecutionPlan compile(PipelineGraph graph, PipelineContext context, Consumer<DomainEvent> hotPathPublisher) {
        Map<String, PipelineNodeDef> nodeDefs = new HashMap<>();
        for (PipelineNodeDef nodeDef : graph.nodes()) {
            nodeDefs.put(nodeDef.id(), nodeDef);
        }

        // Build adjacency lists and compute in-degrees for Kahn's algorithm
        Map<String, List<String>> adjList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (String id : nodeDefs.keySet()) {
            adjList.put(id, new ArrayList<>());
            inDegree.put(id, 0);
        }

        for (PipelineEdgeDef edge : graph.edges()) {
            String source = edge.source();
            String target = edge.target();

            if (nodeDefs.containsKey(source) && nodeDefs.containsKey(target)) {
                adjList.get(source).add(target);
                inDegree.put(target, inDegree.get(target) + 1);
            }
        }

        // Kahn's algorithm
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sortedNodeIds = new ArrayList<>();
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            sortedNodeIds.add(curr);

            for (String neighbor : adjList.get(curr)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sortedNodeIds.size() != graph.nodes().size()) {
            throw new IllegalStateException("Cyclic dependency detected in pipeline graph! Expected "
                    + graph.nodes().size() + " nodes, but sorted " + sortedNodeIds.size());
        }

        // 1. Instantiate node instances (with optional partition support)
        Map<String, PipelineNode> runtimeNodes = new HashMap<>();
        List<PipelineNode> sortedNodes = new ArrayList<>();

        for (String nodeId : sortedNodeIds) {
            PipelineNodeDef def = nodeDefs.get(nodeId);
            PipelineNode runtimeNode;

            int partitions = resolveNodePartitions(def);
            if (partitions > 1) {
                runtimeNode = new PartitionedNode(
                        shardIndex -> nodeFactory.apply(def),
                        partitions,
                        def
                );
            } else {
                runtimeNode = nodeFactory.apply(def);
            }
            runtimeNodes.put(nodeId, runtimeNode);
            sortedNodes.add(runtimeNode);
        }

        // 2. Build routing table
        Map<String, List<PipelineNode>> routingTable = new HashMap<>();
        for (PipelineNodeDef def : graph.nodes()) {
            List<PipelineNode> targets = new ArrayList<>();
            for (String targetId : adjList.get(def.id())) {
                targets.add(runtimeNodes.get(targetId));
            }
            routingTable.put(def.id(), targets);
        }

        // 3. Initialize nodes with their node-specific publishing PipelineContext
        for (String nodeId : sortedNodeIds) {
            PipelineNodeDef def = nodeDefs.get(nodeId);
            PipelineNode runtimeNode = runtimeNodes.get(nodeId);

            PipelineContext nodeContext = new PipelineContext() {
                @Override
                public void publish(DomainEvent event) {
                    if (hotPathPublisher != null) {
                        hotPathPublisher.accept(event);
                        return;
                    }
                    List<PipelineNode> targets = routingTable.get(nodeId);
                    if (targets != null) {
                        for (PipelineNode target : targets) {
                            try {
                                target.onEvent(event);
                            } catch (Exception e) {
                                // Prevent node failure from killing the entire pipeline chain
                            }
                        }
                    }
                }

                @Override
                public long getClockTimeMs() {
                    return context.getClockTimeMs();
                }

                @Override
                public <T> Optional<T> getService(Class<T> serviceType) {
                    return context.getService(serviceType);
                }
            };

            runtimeNode.init(def, nodeContext);
        }

        return new ExecutionPlan(sortedNodes, runtimeNodes, routingTable);
    }

    /**
     * Resolves the partition count for a node definition.
     * Checks node-level config first, then falls back to the compiler default.
     */
    private int resolveNodePartitions(PipelineNodeDef def) {
        if (def.config() != null && def.config().containsKey("partitions")) {
            Object val = def.config().get("partitions");
            if (val instanceof Number n) {
                return n.intValue();
            }
        }
        return defaultPartitions;
    }
}
