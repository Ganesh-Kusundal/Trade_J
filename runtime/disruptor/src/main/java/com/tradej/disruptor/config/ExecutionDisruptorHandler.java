package com.tradej.disruptor.config;

import com.lmax.disruptor.EventHandler;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.disruptor.MutableDomainEventEnvelope;
import com.tradej.execution.service.ExecutionHandler;

import java.util.function.Consumer;

public final class ExecutionDisruptorHandler implements EventHandler<MutableDomainEventEnvelope> {
    private final ExecutionHandler handler;
    private final Consumer<DomainEvent> publisher;
    private final StageTiming timing;

    public ExecutionDisruptorHandler(ExecutionHandler handler, Consumer<DomainEvent> publisher) {
        this(handler, publisher, StageTiming.noOp());
    }

    public ExecutionDisruptorHandler(ExecutionHandler handler, Consumer<DomainEvent> publisher, StageTiming timing) {
        this.handler = handler;
        this.publisher = publisher;
        this.timing = timing;
    }

    @Override
    public void onEvent(MutableDomainEventEnvelope event, long sequence, boolean endOfBatch) {
        long start = System.nanoTime();
        try {
            if (event.event() != null) {
                handler.onDomainEvent(event.event(), publisher);
            }
        } finally {
            timing.record(System.nanoTime() - start);
        }
    }
}
