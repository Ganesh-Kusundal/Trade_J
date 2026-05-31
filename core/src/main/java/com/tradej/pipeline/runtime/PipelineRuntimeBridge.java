package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.graph.PipelineGraph;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Bridge between the Disruptor hot path and the compiled graph runtime.
 * Implemented by {@code PipelineRuntimeService} in the application layer.
 */
public interface PipelineRuntimeBridge {

    AtomicReference<GraphRuntime> runtimeRef();

    PipelineGraph activeGraph();

    void compileHotPath(Consumer<DomainEvent> hotPathPublisher);

    void reload(PipelineGraph graph, Consumer<DomainEvent> hotPathPublisher);
}
