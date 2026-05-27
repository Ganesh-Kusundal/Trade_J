package com.tradej.strategy.service;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.strategy.api.StrategyPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

public final class StrategyEngine {
    private final List<StrategyPlugin> plugins = new ArrayList<>();

    public StrategyEngine(List<StrategyPlugin> plugins) {
        if (plugins != null) {
            this.plugins.addAll(plugins);
        }
        ServiceLoader.load(StrategyPlugin.class).forEach(this.plugins::add);
    }

    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        if (!(event instanceof CandleClosed candleClosed)) {
            return;
        }
        plugins.forEach(plugin -> plugin.onCandleClosed(candleClosed).ifPresent(downstream));
    }
}
