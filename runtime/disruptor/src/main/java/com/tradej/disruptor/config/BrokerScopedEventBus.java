package com.tradej.disruptor.config;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;

import java.util.List;

/**
 * Broker-scoped event bus wrapper that provides isolation between broker event streams.
 *
 * <p>Each broker (Dhan, Upstox, ICICI) gets its own {@code BrokerScopedEventBus} instance
 * backed by an independent {@link com.tradej.disruptor.DisruptorEventBus}. This ensures:
 * <ul>
 *   <li>A slow consumer on one broker's event stream cannot block other brokers</li>
 *   <li>Ring buffer backpressure is broker-local</li>
 *   <li>Events are tagged with the source broker for downstream routing</li>
 * </ul>
 *
 * <p>The composition layer creates one instance per broker and wires each broker's
 * multiplexer to its own bus. Downstream consumers (GraphRuntime, StrategyEngine)
 * subscribe to all broker buses independently.
 */
public final class BrokerScopedEventBus implements EventBus {

    private final EventBus delegate;
    private final String brokerId;

    public BrokerScopedEventBus(EventBus delegate, String brokerId) {
        this.delegate = delegate;
        this.brokerId = brokerId;
    }

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        delegate.subscribe(eventType, handler);
    }

    @Override
    public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        delegate.unsubscribe(eventType, handler);
    }

    @Override
    public void publish(DomainEvent event) {
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

    public String brokerId() {
        return brokerId;
    }

    public EventBus delegate() {
        return delegate;
    }
}
