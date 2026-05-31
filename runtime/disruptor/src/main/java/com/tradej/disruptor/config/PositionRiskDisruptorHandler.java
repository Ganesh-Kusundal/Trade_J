package com.tradej.disruptor.config;

import com.lmax.disruptor.EventHandler;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.disruptor.MutableDomainEventEnvelope;
import com.tradej.execution.risk.PositionRiskHandler;

import java.util.function.Consumer;

public final class PositionRiskDisruptorHandler implements EventHandler<MutableDomainEventEnvelope> {
    private final PositionRiskHandler handler;
    private final Consumer<DomainEvent> publisher;
    private final StageTiming timing;

    public PositionRiskDisruptorHandler(PositionRiskHandler handler, Consumer<DomainEvent> publisher) {
        this(handler, publisher, StageTiming.noOp());
    }

    public PositionRiskDisruptorHandler(PositionRiskHandler handler, Consumer<DomainEvent> publisher, StageTiming timing) {
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
