package com.tradej.strategy.plugin;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.value.Side;
import com.tradej.feature.store.OptionsAwareFeatureStore;
import com.tradej.strategy.api.GraphStrategyPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Example options-aware strategy: emits a signal when IV and delta context are present
 * and price is above max-pain strike (bullish bias filter).
 */
public final class OptionsContextStrategyPlugin implements GraphStrategyPlugin {

    private final OptionsAwareFeatureStore featureStore;

    /**
     * No-arg constructor required by the {@link java.util.ServiceLoader} SPI
     * so this plugin can be discovered via
     * {@code META-INF/services/com.tradej.strategy.api.GraphStrategyPlugin}.
     * The {@link #featureStore} is null and {@link #onEvent(DomainEvent)} will
     * short-circuit returning {@link Optional#empty()} until the Spring
     * container replaces this instance with a fully-wired one through
     * {@code TradingConfiguration#optionsContextStrategyPlugin}.
     */
    public OptionsContextStrategyPlugin() {
        this(null);
    }

    public OptionsContextStrategyPlugin(OptionsAwareFeatureStore featureStore) {
        this.featureStore = featureStore;
    }

    @Override
    public String name() {
        return "options-context";
    }

    @Override
    public List<Class<? extends DomainEvent>> subscribedEventTypes() {
        return List.of(CandleClosed.class);
    }

    @Override
    public Optional<SignalGenerated> onEvent(DomainEvent event) {
        if (featureStore == null) {
            // SPI-instantiated placeholder — the Spring-managed bean
            // (with a fully-wired featureStore) is the active one.
            return Optional.empty();
        }
        if (!(event instanceof CandleClosed closed)) {
            return Optional.empty();
        }
        String symbol = closed.candle().symbol();
        Map<String, Object> ctx = featureStore.optionsContext(symbol);
        if (ctx.isEmpty()) {
            return Optional.empty();
        }
        Object maxPainObj = ctx.get("maxPainStrikePaisa");
        Object ivObj = ctx.get("iv");
        if (!(maxPainObj instanceof Number maxPain) || !(ivObj instanceof Number iv)) {
            return Optional.empty();
        }
        if (iv.doubleValue() <= 0 || closed.candle().closePaisa() <= maxPain.longValue()) {
            return Optional.empty();
        }
        return Optional.of(new SignalGenerated(
                EventMetadata.root(),
                UUID.randomUUID().toString(),
                symbol,
                closed.candle().interval(),
                Side.BUY,
                closed.candle().closePaisa(),
                0L,
                0L,
                "options-iv-above-maxpain",
                Map.of(
                        "strategyName", name(),
                        "iv", iv.doubleValue(),
                        "maxPainStrikePaisa", maxPain.longValue(),
                        "quantity", 1L
                )
        ));
    }
}
