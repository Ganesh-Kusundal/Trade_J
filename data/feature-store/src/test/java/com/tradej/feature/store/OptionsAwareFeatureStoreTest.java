package com.tradej.feature.store;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("unit")
class OptionsAwareFeatureStoreTest {

    @Test
    void feedsOptionsContextForStrategies() {
        OptionsAwareFeatureStore store = new OptionsAwareFeatureStore(new InMemoryFeatureStore());
        Instrument underlying = new Instrument(
                "NIFTY", "NIFTY", Exchange.NSE, ExchangeSegment.NSE_FNO,
                "INDEX", "NIFTY", null, null, null, 1, 0);
        store.feed(new OptionChainUpdated(
                EventMetadata.root(),
                new OptionChainSnapshot(underlying, LocalDate.now(), 22_000_00L, List.of())
        ));
        store.feed(new MaxPainComputed(
                EventMetadata.root(), "NIFTY", LocalDate.now(), 21_800_00L, 1_000_00L
        ));

        Map<String, Object> ctx = store.optionsContext("NIFTY");
        assertEquals(22_000_00L, ctx.get("spotPricePaisa"));
        assertEquals(21_800_00L, ctx.get("maxPainStrikePaisa"));

        Candle candle = new Candle("NIFTY", "5m", 1L, 2L, 1L, 1L, 1L, 1L, 1L, true);
        store.feed(new CandleClosed(EventMetadata.root(), candle));
        assertFalse(store.getFeatures("NIFTY", "5m", 5).isPresent());
    }
}
