package com.tradej.disruptor;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decorator around an existing {@link EventBus} that propagates correlation IDs
 * via {@link TraceContext}.
 *
 * <p>On {@link #subscribe}, the wrapped handler is instrumented so that
 * {@link TraceContext#fromEvent} is called before dispatching to the real handler,
 * and {@link TraceContext#clear()} is called in a {@code finally} block to prevent
 * thread-local leakage.
 *
 * <p>On {@link #publish}, if a correlation ID is already set on the current thread,
 * it is logged at DEBUG level for observability.
 */
public final class TracingEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(TracingEventBus.class);

    private final EventBus delegate;

    public TracingEventBus(EventBus delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        DomainEventHandler<T> tracingHandler = event -> {
            try {
                TraceContext.fromEvent(event.metadata());
                handler.onEvent(event);
            } finally {
                TraceContext.clear();
            }
        };
        delegate.subscribe(eventType, tracingHandler);
    }

    @Override
    public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        delegate.unsubscribe(eventType, handler);
    }

    @Override
    public void publish(DomainEvent event) {
        String correlationId = TraceContext.getCorrelationId();
        if (correlationId != null) {
            log.debug("Publishing {} with correlationId={}", event.getClass().getSimpleName(), correlationId);
        }
        delegate.publish(event);
    }

    @Override
    public void publishBatch(List<? extends DomainEvent> events) {
        delegate.publishBatch(events);
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }
}
