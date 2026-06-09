package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Optional mixin interface that {@link PipelineNode} implementations can adopt
 * to expose a reactive {@link Flux} of their output events for cold-path subscribers.
 * <p>
 * When a graph contains reactive nodes, the {@code ReactorBridge} merges their
 * output streams into a single composable cold-path pipeline.
 */
public interface ReactivePipelineNode {

    /**
     * Returns a reactive stream of events produced by this node.
     * Subscribers receive events asynchronously via Reactor's scheduler.
     */
    Flux<DomainEvent> outputEvents();

    /**
     * Creates a multicast sink with backpressure buffering for node output events.
     *
     * @param bufferSize maximum number of buffered events before backpressure applies
     * @return a thread-safe sink for emitting events
     */
    static Sinks.Many<DomainEvent> createSink(int bufferSize) {
        return Sinks.many().multicast().onBackpressureBuffer(bufferSize);
    }

    /**
     * Safely emits an event into the sink, swallowing backpressure failures
     * rather than blocking the hot path.
     *
     * @param sink  the sink to emit into
     * @param event the event to emit
     */
    static void tryEmit(Sinks.Many<DomainEvent> sink, DomainEvent event) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result != Sinks.EmitResult.OK && result != Sinks.EmitResult.FAIL_OVERFLOW) {
            // Backpressure or cancelled — drop silently for hot-path safety
        }
    }
}
