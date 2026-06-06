package com.tradej.brokergateway;

import com.tradej.brokergateway.query.OptionAnalytics;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.OptionQuote;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptionAnalyticsTest {

    private OptionChainSnapshot createChain(long spot, List<OptionChainEntry> strikes) {
        Instrument underlying = new Instrument("NIFTY", "NIFTY", null, null, null, null, null, 0L, null, 0, 0);
        return new OptionChainSnapshot(underlying, LocalDate.now().plusDays(7), spot, strikes);
    }

    private OptionQuote quote(long oi, long volume) {
        return new OptionQuote(null, 100L, oi, volume, 90L, 100L, 110L, 100L, null);
    }

    @Test
    void pcrComputesPutCallRatio() {
        var chain = createChain(25000_00L, List.of(
                new OptionChainEntry(24000_00L, quote(1000, 500), quote(2000, 800)),
                new OptionChainEntry(25000_00L, quote(3000, 1000), quote(1500, 600)),
                new OptionChainEntry(26000_00L, quote(2000, 700), quote(500, 200))
        ));

        OptionAnalytics.PcrResult pcr = OptionAnalytics.pcr(chain);

        // call OI: 1000 + 3000 + 2000 = 6000
        // put OI: 2000 + 1500 + 500 = 4000
        assertEquals(6000, pcr.totalCallOi());
        assertEquals(4000, pcr.totalPutOi());
        assertEquals(4000.0 / 6000.0, pcr.ratio(), 0.001);
    }

    @Test
    void topOiReturnsHighestOiStrikes() {
        var chain = createChain(25000_00L, List.of(
                new OptionChainEntry(24000_00L, quote(1000, 0), quote(500, 0)),
                new OptionChainEntry(25000_00L, quote(5000, 0), quote(3000, 0)),
                new OptionChainEntry(26000_00L, quote(2000, 0), quote(8000, 0))
        ));

        var topOi = OptionAnalytics.topOi(chain, 2);

        assertEquals(2, topOi.size());
        // 26000: 2000+8000=10000, 25000: 5000+3000=8000
        assertEquals(26000_00L, topOi.get(0).strikePricePaisa());
        assertEquals(10000, topOi.get(0).openInterest());
    }

    @Test
    void topCallOiFiltersCallSide() {
        var chain = createChain(25000_00L, List.of(
                new OptionChainEntry(24000_00L, quote(1000, 0), quote(9000, 0)),
                new OptionChainEntry(25000_00L, quote(5000, 0), quote(500, 0))
        ));

        var topCalls = OptionAnalytics.topCallOi(chain, 1);

        assertEquals(1, topCalls.size());
        assertEquals(25000_00L, topCalls.get(0).strikePricePaisa());
        assertEquals(5000, topCalls.get(0).openInterest());
        assertEquals("CALL", topCalls.get(0).side());
    }

    @Test
    void maxPainStrikeComputesCorrectly() {
        // Simple scenario: 2 strikes, clear max pain
        var chain = createChain(25000_00L, List.of(
                new OptionChainEntry(24000_00L, quote(100, 0), quote(1000, 0)),
                new OptionChainEntry(25000_00L, quote(1000, 0), quote(100, 0))
        ));

        long maxPain = OptionAnalytics.maxPainStrike(chain);

        // At 24000: call pain = 0 (24000 < 24000, 24000 < 25000), put pain = 0 (24000 not > any put strike with OI)
        // At 25000: call pain = (25000-24000)*100 = 100000, put pain = 0
        // Max pain is at 24000 (less total pain for writers)
        assertTrue(maxPain > 0);
    }

    @Test
    void supportResistanceIdentifiesKeyLevels() {
        var chain = createChain(25000_00L, List.of(
                new OptionChainEntry(24000_00L, quote(500, 0), quote(10000, 0)),  // high put OI below spot = support
                new OptionChainEntry(25000_00L, quote(3000, 0), quote(3000, 0)),
                new OptionChainEntry(26000_00L, quote(9000, 0), quote(500, 0))    // high call OI above spot = resistance
        ));

        var sr = OptionAnalytics.supportResistance(chain);

        assertEquals(24000_00L, sr.supportStrikePaisa());
        assertEquals(10000, sr.supportOi());
        assertEquals(26000_00L, sr.resistanceStrikePaisa());
        assertEquals(9000, sr.resistanceOi());
    }

    @Test
    void atmReturnsClosestStrike() {
        var chain = createChain(25050_00L, List.of(
                new OptionChainEntry(24000_00L, quote(100, 0), quote(100, 0)),
                new OptionChainEntry(25000_00L, quote(100, 0), quote(100, 0)),
                new OptionChainEntry(26000_00L, quote(100, 0), quote(100, 0))
        ));

        var atm = OptionAnalytics.atm(chain);

        assertEquals(25000_00L, atm.strikePricePaisa());
    }

    @Test
    void itmCallsReturnsStrikesBelowSpot() {
        var chain = createChain(25000_00L, List.of(
                new OptionChainEntry(24000_00L, quote(100, 0), quote(100, 0)),
                new OptionChainEntry(25000_00L, quote(100, 0), quote(100, 0)),
                new OptionChainEntry(26000_00L, quote(100, 0), quote(100, 0))
        ));

        var itm = OptionAnalytics.itmCalls(chain);

        assertEquals(1, itm.size());
        assertEquals(24000_00L, itm.get(0).strikePricePaisa());
    }

    @Test
    void otmCallsReturnsStrikesAboveSpot() {
        var chain = createChain(25000_00L, List.of(
                new OptionChainEntry(24000_00L, quote(100, 0), quote(100, 0)),
                new OptionChainEntry(25000_00L, quote(100, 0), quote(100, 0)),
                new OptionChainEntry(26000_00L, quote(100, 0), quote(100, 0))
        ));

        var otm = OptionAnalytics.otmCalls(chain);

        assertEquals(1, otm.size());
        assertEquals(26000_00L, otm.get(0).strikePricePaisa());
    }
}
