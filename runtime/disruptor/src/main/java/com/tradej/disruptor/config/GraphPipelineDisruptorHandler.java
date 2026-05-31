package com.tradej.disruptor.config;

import com.lmax.disruptor.EventHandler;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.disruptor.MutableDomainEventEnvelope;
import com.tradej.pipeline.runtime.GraphRuntime;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Disruptor stage that executes the compiled pipeline graph in topological order.
 * Replaces the hardcoded risk→candle→feature→strategy→execution handler chain.
 */
public final class GraphPipelineDisruptorHandler implements EventHandler<MutableDomainEventEnvelope> {

    private final Supplier<GraphRuntime> runtimeSupplier;
    private final StageTiming timing;

    public GraphPipelineDisruptorHandler(GraphRuntime graphRuntime, StageTiming timing) {
        this(() -> graphRuntime, timing);
    }

    public GraphPipelineDisruptorHandler(AtomicReference<GraphRuntime> runtimeRef, StageTiming timing) {
        this(runtimeRef::get, timing);
    }

    public GraphPipelineDisruptorHandler(Supplier<GraphRuntime> runtimeSupplier, StageTiming timing) {
        this.runtimeSupplier = runtimeSupplier;
        this.timing = timing == null ? StageTiming.noOp() : timing;
    }

    @Override
    public void onEvent(MutableDomainEventEnvelope envelope, long sequence, boolean endOfBatch) {
        DomainEvent event = envelope.event();
        if (event == null) {
            return;
        }
        GraphRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            return;
        }
        long start = System.nanoTime();
        try {
            runtime.processSequential(event);
        } finally {
            timing.record(System.nanoTime() - start);
        }
    }
}
