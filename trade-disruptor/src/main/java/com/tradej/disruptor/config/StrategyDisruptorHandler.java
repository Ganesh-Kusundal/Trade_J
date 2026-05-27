package com.tradej.disruptor.config;

import com.lmax.disruptor.EventHandler;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.disruptor.MutableDomainEventEnvelope;
import com.tradej.strategy.service.StrategyEngine;

import java.util.function.Consumer;

public final class StrategyDisruptorHandler implements EventHandler<MutableDomainEventEnvelope> {
    private final StrategyEngine handler;
    private final Consumer<DomainEvent> publisher;

    public StrategyDisruptorHandler(StrategyEngine handler, Consumer<DomainEvent> publisher) {
        this.handler = handler;
        this.publisher = publisher;
    }

    @Override
    public void onEvent(MutableDomainEventEnvelope event, long sequence, boolean endOfBatch) {
        if (event.event() != null) {
            handler.onDomainEvent(event.event(), publisher);
        }
    }
}
