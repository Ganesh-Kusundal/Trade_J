package com.tradej.indicators.spi;

import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import com.tradej.indicators.TickLevelCVD;
import com.tradej.indicators.VolumeProfile;
import com.tradej.indicators.spi.builtin.TickLevelCVDNonCandleProvider;
import com.tradej.indicators.spi.builtin.VolumeProfileNonCandleProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class NonCandleIndicatorRegistryTest {

    private static Trade trade(long pricePaisa, long quantity) {
        return new Trade("t-" + pricePaisa + "-" + quantity,
                "o-1", "TEST", ExchangeSegment.NSE_EQ,
                Side.BUY, quantity, pricePaisa, 0L);
    }

    @Test
    void discoverAll() {
        NonCandleIndicatorRegistry registry = NonCandleIndicatorRegistry.discover();
        assertTrue(registry.size() >= 2,
                "Expected at least 2 non-candle providers, got " + registry.size());
        assertTrue(registry.has("volume-profile"));
        assertTrue(registry.has("tick-level-cvd"));
    }

    @Test
    void getByNameVolumeProfile() {
        NonCandleIndicatorRegistry registry = NonCandleIndicatorRegistry.discover();
        NonCandleIndicatorProvider provider = registry.get("volume-profile")
                .orElseThrow(() -> new NoSuchElementException("volume-profile"));
        VolumeProfile vp = provider.computeFromTrades(List.of(
                trade(250000, 500),
                trade(250100, 300),
                trade(250000, 200)));
        assertNotNull(vp);
        assertEquals(1000L, vp.totalVolume());
        assertEquals(700L, vp.volumeAt(250000));
    }

    @Test
    void getByNameTickLevelCVD() {
        NonCandleIndicatorRegistry registry = NonCandleIndicatorRegistry.discover();
        NonCandleIndicatorProvider provider = registry.get("tick-level-cvd")
                .orElseThrow(() -> new NoSuchElementException("tick-level-cvd"));
        TickLevelCVD.CvdSnapshot snap = provider.computeFromTicks(List.of(
                new long[]{250000, 100},  // first tick — buy
                new long[]{250100, 50},   // uptick — buy
                new long[]{249900, 200})); // downtick — sell
        assertNotNull(snap);
        assertEquals(150L, snap.buyVolume());
        assertEquals(200L, snap.sellVolume());
        assertEquals(-50L, snap.cvd());
    }

    @Test
    void unknownNameThrows() {
        NonCandleIndicatorRegistry registry = NonCandleIndicatorRegistry.discover();
        assertTrue(registry.get("nope").isEmpty());
    }

    @Test
    void ofFactoryReturnsGivenProviders() {
        VolumeProfileNonCandleProvider vp = new VolumeProfileNonCandleProvider();
        TickLevelCVDNonCandleProvider cvd = new TickLevelCVDNonCandleProvider();
        NonCandleIndicatorRegistry registry = NonCandleIndicatorRegistry.of(vp, cvd);

        assertEquals(2, registry.size());
        assertSame(vp, registry.get("volume-profile").orElse(null));
        assertSame(cvd, registry.get("tick-level-cvd").orElse(null));
    }

    @Test
    void unsupportedInputShapeThrows() {
        NonCandleIndicatorProvider vp = new VolumeProfileNonCandleProvider();
        assertThrows(UnsupportedOperationException.class,
                () -> vp.computeFromTicks(List.of()));

        NonCandleIndicatorProvider cvd = new TickLevelCVDNonCandleProvider();
        assertThrows(UnsupportedOperationException.class,
                () -> cvd.computeFromTrades(List.of()));
    }
}
