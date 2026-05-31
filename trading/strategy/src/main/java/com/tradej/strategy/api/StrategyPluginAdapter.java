package com.tradej.strategy.api;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.SignalGenerated;

import java.util.List;
import java.util.Optional;

/**
 * Adapter that wraps a legacy {@link StrategyPlugin} (candle-only) as a
 * {@link GraphStrategyPlugin} for the unified graph sandbox.
 *
 * <p>This enables gradual migration: existing candle-only strategy plugins
 * continue to work through the {@link GraphStrategySandbox} without any
 * code changes. When all plugins are migrated to {@link GraphStrategyPlugin},
 * this adapter and the legacy {@link StrategyPlugin} interface can be removed.
 */
public final class StrategyPluginAdapter implements GraphStrategyPlugin {

    private final StrategyPlugin delegate;

    public StrategyPluginAdapter(StrategyPlugin delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public List<Class<? extends DomainEvent>> subscribedEventTypes() {
        return List.of(CandleClosed.class);
    }

    @Override
    public Optional<SignalGenerated> onEvent(DomainEvent event) {
        if (event instanceof CandleClosed candleClosed) {
            return delegate.onCandleClosed(candleClosed);
        }
        return Optional.empty();
    }

    /**
     * Returns the underlying legacy plugin.
     * Used for backward-compatible identity checks and serialization.
     */
    public StrategyPlugin delegate() {
        return delegate;
    }
}
