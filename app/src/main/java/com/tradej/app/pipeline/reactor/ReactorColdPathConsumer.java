package com.tradej.app.pipeline.reactor;

import com.tradej.core.domain.event.DomainEvent;

/**
 * Optional extension point for additional Reactor cold-path consumers.
 * Implementations are registered by {@link ReactorColdPathRegistry} without duplicating
 * existing async-dispatch subscribers such as {@code DuckDbEventStore}.
 */
@FunctionalInterface
public interface ReactorColdPathConsumer {

    void onEvent(DomainEvent event);
}
