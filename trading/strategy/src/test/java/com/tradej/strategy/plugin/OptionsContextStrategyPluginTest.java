package com.tradej.strategy.plugin;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.feature.store.InMemoryFeatureStore;
import com.tradej.feature.store.OptionsAwareFeatureStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class OptionsContextStrategyPluginTest {

    @Test
    void emitsSignalWhenPriceAboveMaxPainWithIvContext() {
        OptionsAwareFeatureStore store = new OptionsAwareFeatureStore(new InMemoryFeatureStore());
        Instrument underlying = new Instrument(
                "SBIN", "SBIN", Exchange.NSE, ExchangeSegment.NSE_EQ,
                "EQUITY", "SBIN", null, null, null, 1, 0);
        store.feed(new OptionChainUpdated(
                EventMetadata.root(),
                new OptionChainSnapshot(underlying, LocalDate.now(), 500_00L, List.of())
        ));
        store.feed(new MaxPainComputed(
                EventMetadata.root(), "SBIN", LocalDate.now(), 480_00L, 1_00L
        ));
        store.feed(new com.tradej.core.domain.event.GreeksComputed(
                EventMetadata.root(),
                com.tradej.core.domain.model.InstrumentKey.of("SBIN", ExchangeSegment.NSE_EQ),
                new com.tradej.core.domain.model.OptionGreeks(0.5, -0.1, 0.01, 0.2, 0.25)
        ));

        OptionsContextStrategyPlugin plugin = new OptionsContextStrategyPlugin(store);
        Candle candle = new Candle("SBIN", "5m", 1L, 2L, 490_00L, 510_00L, 485_00L, 500_00L, 1000L, true);
        var signal = plugin.onEvent(new CandleClosed(EventMetadata.root(), candle));
        assertTrue(signal.isPresent());
        assertEquals("options-iv-above-maxpain", signal.get().setup());
    }
}
