package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import com.tradej.indicators.VolumeProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class VolumeProfileNonCandleProviderTest {

    private static Trade trade(long pricePaisa, long quantity) {
        return new Trade("t-" + pricePaisa + "-" + quantity,
                "o-1", "TEST", ExchangeSegment.NSE_EQ,
                Side.BUY, quantity, pricePaisa, 0L);
    }

    private static List<Trade> sampleTrades() {
        List<Trade> trades = new ArrayList<>();
        trades.add(trade(250000, 500));
        trades.add(trade(250100, 300));
        trades.add(trade(250000, 200));
        trades.add(trade(250200, 800));
        return trades;
    }

    @Test
    void metadataIsStable() {
        VolumeProfileNonCandleProvider p = new VolumeProfileNonCandleProvider();
        assertEquals("volume-profile", p.name());
        assertEquals("Volume Profile", p.displayName());
        assertEquals("1.0.0", p.version());
    }

    @Test
    void computeFromTradesProducesPopulatedProfile() {
        VolumeProfileNonCandleProvider p = new VolumeProfileNonCandleProvider();
        VolumeProfile vp = p.computeFromTrades(sampleTrades());
        assertNotNull(vp);
        // Total volume is the sum of all trade quantities.
        assertEquals(500L + 300L + 200L + 800L, vp.totalVolume());
        // Volume at a known snapped price must be the sum of trades at that level.
        assertEquals(700L, vp.volumeAt(250000));
        // POC must be a price level we actually traded at.
        long poc = vp.pointOfControl();
        assertTrue(poc == 250000L || poc == 250200L,
                "POC should be one of the highest-volume levels, got " + poc);
    }

    @Test
    void computeFromTicksIsUnsupported() {
        VolumeProfileNonCandleProvider p = new VolumeProfileNonCandleProvider();
        // VolumeProfile is trade-based; ticks are not its input shape.
        assertThrows(UnsupportedOperationException.class,
                () -> p.computeFromTicks(List.of()));
    }
}
