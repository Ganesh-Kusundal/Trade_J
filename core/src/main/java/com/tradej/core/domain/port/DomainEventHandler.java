package com.tradej.core.domain.port;

import com.tradej.core.domain.event.DomainEvent;

/**
 * Functional interface for consuming domain events.
 *
 * <p>Implementations must not throw checked exceptions. Any recoverable error
 * should be wrapped in an unchecked exception; the {@link EventBus} dispatch
 * layer will catch and log it so one failing handler cannot block others.
 */
@FunctionalInterface
public interface DomainEventHandler<T extends DomainEvent> {
    void onEvent(T event);
}
