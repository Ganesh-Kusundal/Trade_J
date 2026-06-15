package com.tradej.strategy.plugin;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.id.IdGenerator;
import com.tradej.core.domain.id.UuidIdGenerator;
import com.tradej.core.domain.value.Side;
import com.tradej.feature.store.OptionsAwareFeatureStore;
import com.tradej.strategy.api.GraphStrategyPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Example options-aware strategy: emits a signal when IV and delta context are present
 * and price is above max-pain strike (bullish bias filter).
 */
public final class OptionsContextStrategyPlugin implements GraphStrategyPlugin {

    private final OptionsAwareFeatureStore featureStore;
    private final IdGenerator idGenerator;

    public OptionsContextStrategyPlugin(OptionsAwareFeatureStore featureStore) {
        this(featureStore, new UuidIdGenerator());
    }

    public OptionsContextStrategyPlugin(OptionsAwareFeatureStore featureStore, IdGenerator idGenerator) {
        this.featureStore = featureStore;
        this.idGenerator = idGenerator != null ? idGenerator : new UuidIdGenerator();
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
                idGenerator.generateSignalId(),
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
