package com.tradej.strategy.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.strategy.api.StrategyPlugin;

import java.util.List;
import java.util.function.Consumer;

/**
 * Strategy evaluation engine backed by an isolated {@link StrategySandbox}.
 * Each strategy plugin runs in its own virtual thread with a bounded timeout,
 * so that a crashing or hanging plugin cannot block others.
 *
 * <p>This class is a thin facade that delegates to {@link StrategySandbox}
 * for all event processing. Existing callers continue to work unchanged.
 *
 * @deprecated Use {@link GraphStrategySandbox} which supports all event types
 * (tick, depth, candle, ML) through the {@link com.tradej.strategy.api.GraphStrategyPlugin} interface.
 * Scheduled for removal after all plugins are migrated to GraphStrategyPlugin.
 */
@Deprecated(since = "2.0")
public final class StrategyEngine {

    private final StrategySandbox sandbox;

    /**
     * Creates a strategy engine backed by a sandbox with the given plugins.
     * Plugins loaded via {@link java.util.ServiceLoader} are also included.
     */
    public StrategyEngine(List<StrategyPlugin> plugins, EventMetadataFactory eventMetadataFactory) {
        this.sandbox = new StrategySandbox(plugins, eventMetadataFactory);
    }

    /**
     * Evaluates the event against all registered strategy plugins through
     * the isolated sandbox. Delegates directly to
     * {@link StrategySandbox#onDomainEvent(DomainEvent, Consumer)}.
     */
    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        sandbox.onDomainEvent(event, downstream);
    }

    /**
     * Returns the names of all registered strategy plugins.
     */
    public List<String> pluginNames() {
        return sandbox.pluginNames();
    }

    /**
     * Shuts down the sandbox executor. Called automatically by Spring when the
     * application context closes (via {@code @Bean(destroyMethod = "shutdown")}).
     */
    public void shutdown() {
        sandbox.shutdown();
    }
}
