package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import java.util.Optional;

/**
 * Execution context provided to nodes during event propagation.
 * Allows nodes to query the runtime state of preceding nodes, register dynamic
 * metrics, and query virtual clocks during backtesting/replay.
 */
public interface PipelineContext {

    void publish(DomainEvent event);

    long getClockTimeMs();

    <T> Optional<T> getService(Class<T> serviceType);
}
