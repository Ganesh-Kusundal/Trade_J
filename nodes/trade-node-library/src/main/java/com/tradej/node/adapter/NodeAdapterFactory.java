package com.tradej.node.adapter;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.node.NodeContext;
import com.tradej.node.NodeDescriptor;
import com.tradej.node.NodeExecutor;
import com.tradej.node.NodeResult;
import com.tradej.node.NodeCategory;
import com.tradej.node.PortDescriptor;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.pipeline.runtime.PipelineContext;
import com.tradej.pipeline.runtime.NodeMetrics;
import com.tradej.pipeline.runtime.NodeState;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class NodeAdapterFactory {

    private final Map<Class<? extends BasePipelineNode>, NodeDescriptor> descriptorCache = new ConcurrentHashMap<>();

    public NodeExecutor adapt(BasePipelineNode node, NodeDescriptor descriptor) {
        return new AdaptedNodeExecutor(Objects.requireNonNull(node, "node"),
                Objects.requireNonNull(descriptor, "descriptor"));
    }

    public NodeExecutor adapt(BasePipelineNode node, String nodeType, String displayName,
                               NodeCategory category, List<PortDescriptor> inputs,
                               List<PortDescriptor> outputs) {
        NodeDescriptor descriptor = new NodeDescriptor(
                nodeType, displayName, "", category,
                List.of(), inputs, outputs, Map.of());
        return adapt(node, descriptor);
    }

    public NodeDescriptor descriptorFor(Class<? extends BasePipelineNode> nodeClass,
                                         String nodeType, String displayName,
                                         NodeCategory category) {
        return descriptorCache.computeIfAbsent(nodeClass, k ->
                new NodeDescriptor(nodeType, displayName, "", category,
                        List.of(), List.of(), List.of(), Map.of()));
    }

    private static final class AdaptedNodeExecutor implements NodeExecutor {

        private final BasePipelineNode delegate;
        private final NodeDescriptor descriptor;
        private final DomainEventCollector collector = new DomainEventCollector();

        AdaptedNodeExecutor(BasePipelineNode delegate, NodeDescriptor descriptor) {
            this.delegate = delegate;
            this.descriptor = descriptor;
        }

        @Override
        public NodeDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public NodeResult execute(NodeContext context, Map<String, Object> config) {
            collector.clear();
            PipelineContext pipelineCtx = new AdapterPipelineContext(context, collector);
            PipelineNodeDef nodeDef = new PipelineNodeDef(
                    descriptor.nodeType(),
                    descriptor.nodeType(),
                    descriptor.displayName(),
                    config
            );
            try {
                delegate.init(nodeDef, pipelineCtx);
                Object payload = config.get("payload");
                if (payload instanceof DomainEvent event) {
                    delegate.onEvent(event);
                }
                NodeState state = delegate.getState();
                if (state == NodeState.FAILED) {
                    return NodeResult.fail("Node execution failed");
                }
                List<Object> outputs = collector.collect();
                if (outputs.isEmpty()) {
                    return NodeResult.ok("default", null);
                }
                return NodeResult.ok("default", outputs.get(outputs.size() - 1));
            } catch (Exception e) {
                return NodeResult.fail(e.getMessage());
            } finally {
                delegate.destroy();
            }
        }
    }

    private static final class DomainEventCollector implements Consumer<DomainEvent> {
        private final List<DomainEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        void clear() { events.clear(); }

        @Override
        public void accept(DomainEvent event) { events.add(event); }

        List<Object> collect() {
            return new java.util.ArrayList<>(events);
        }
    }

    private static final class AdapterPipelineContext implements PipelineContext {
        private final NodeContext nodeContext;
        private final DomainEventCollector collector;

        AdapterPipelineContext(NodeContext nodeContext, DomainEventCollector collector) {
            this.nodeContext = nodeContext;
            this.collector = collector;
        }

        @Override
        public void publish(DomainEvent event) {
            collector.accept(event);
        }

        @Override
        public long getClockTimeMs() {
            return nodeContext.now().toEpochMilli();
        }

        @Override
        public <T> java.util.Optional<T> getService(Class<T> serviceType) {
            return java.util.Optional.empty();
        }
    }
}