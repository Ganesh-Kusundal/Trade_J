package com.tradej.disruptor.config;

import com.lmax.disruptor.EventHandler;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.disruptor.MutableDomainEventEnvelope;
import com.tradej.strategy.service.GraphStrategySandbox;

import java.util.function.Consumer;

/**
 * Disruptor event handler that delegates to {@link GraphStrategySandbox} for
 * tick-level, depth-level, and multi-event strategy evaluation.
 *
 * <p>Unlike {@link StrategyDisruptorHandler} (candle-only), this handler
 * dispatches any event type that the registered {@code GraphStrategyPlugin}
 * instances subscribe to via {@link com.tradej.strategy.api.GraphStrategyPlugin#subscribedEventTypes()}.
 *
 * <p>Both handlers coexist during migration: legacy candle-only strategies
 * run through {@code StrategyDisruptorHandler}, while tick/depth/ML strategies
 * run through this handler. Once all plugins are migrated to {@code GraphStrategyPlugin},
 * the legacy handler can be removed.
 */
public final class GraphStrategyDisruptorHandler implements EventHandler<MutableDomainEventEnvelope> {

    private final GraphStrategySandbox sandbox;
    private final Consumer<DomainEvent> publisher;
    private final StageTiming timing;

    public GraphStrategyDisruptorHandler(GraphStrategySandbox sandbox, Consumer<DomainEvent> publisher) {
        this(sandbox, publisher, StageTiming.noOp());
    }

    public GraphStrategyDisruptorHandler(GraphStrategySandbox sandbox, Consumer<DomainEvent> publisher, StageTiming timing) {
        this.sandbox = sandbox;
        this.publisher = publisher;
        this.timing = timing;
    }

    @Override
    public void onEvent(MutableDomainEventEnvelope event, long sequence, boolean endOfBatch) {
        long start = System.nanoTime();
        try {
            if (event.event() != null) {
                sandbox.onDomainEvent(event.event(), publisher);
            }
        } finally {
            timing.record(System.nanoTime() - start);
        }
    }
}
