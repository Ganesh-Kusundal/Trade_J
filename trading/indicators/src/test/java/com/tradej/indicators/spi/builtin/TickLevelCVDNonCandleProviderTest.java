package com.tradej.indicators.spi.builtin;

import com.tradej.indicators.TickLevelCVD;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class TickLevelCVDNonCandleProviderTest {

    private static long[] tick(long pricePaisa, long volume) {
        return new long[]{pricePaisa, volume};
    }

    private static List<long[]> sampleTicks() {
        List<long[]> ticks = new ArrayList<>();
        // First tick is convention-buy.
        ticks.add(tick(250000, 100));
        // Uptick → buy.
        ticks.add(tick(250100, 50));
        // Downtick → sell.
        ticks.add(tick(249900, 200));
        // Unchanged → buy.
        ticks.add(tick(249900, 25));
        return ticks;
    }

    @Test
    void metadataIsStable() {
        TickLevelCVDNonCandleProvider p = new TickLevelCVDNonCandleProvider();
        assertEquals("tick-level-cvd", p.name());
        assertEquals("Tick-Level Cumulative Volume Delta", p.displayName());
        assertEquals("1.0.0", p.version());
    }

    @Test
    void computeFromTicksProducesSnapshot() {
        TickLevelCVDNonCandleProvider p = new TickLevelCVDNonCandleProvider();
        TickLevelCVD.CvdSnapshot snap = p.computeFromTicks(sampleTicks());
        assertNotNull(snap);
        // Buy: 100 (first) + 50 (uptick) + 25 (unchanged) = 175
        assertEquals(175L, snap.buyVolume());
        // Sell: 200 (downtick)
        assertEquals(200L, snap.sellVolume());
        // CVD: 100 + 50 - 200 + 25 = -25
        assertEquals(-25L, snap.cvd());
    }

    @Test
    void computeFromTradesIsUnsupported() {
        TickLevelCVDNonCandleProvider p = new TickLevelCVDNonCandleProvider();
        // TickLevelCVD is tick-based; trades are not its input shape.
        assertThrows(UnsupportedOperationException.class,
                () -> p.computeFromTrades(List.of()));
    }
}
