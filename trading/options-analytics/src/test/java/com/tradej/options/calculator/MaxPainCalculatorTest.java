package com.tradej.options.calculator;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MaxPainCalculatorTest {

    private static final Instrument NIFTY = new Instrument(
            "NIFTY", "NIFTY", Exchange.NSE, ExchangeSegment.IDX_I,
            "INDEX", null, null, null, null, 50L, 50L);

    private static OptionQuote quote(long oi) {
        return new OptionQuote(null, 0L, oi, 0L, 0L, 0L, 0L, 0L, null);
    }

    @Test
    void emptyChain_returnsZero() {
        OptionChainSnapshot chain = new OptionChainSnapshot(NIFTY, LocalDate.now(), 24000_00L, List.of());
        MaxPainCalculator.MaxPainResult result = MaxPainCalculator.compute(chain);
        assertEquals(0L, result.strikePaisa());
    }

    @Test
    void nullChain_returnsZero() {
        MaxPainCalculator.MaxPainResult result = MaxPainCalculator.compute(null);
        assertEquals(0L, result.strikePaisa());
    }

    @Test
    void singleStrike_returnsThatStrike() {
        OptionChainEntry entry = new OptionChainEntry(24000_00L, quote(1000), quote(1000));
        OptionChainSnapshot chain = new OptionChainSnapshot(NIFTY, LocalDate.now(), 24000_00L, List.of(entry));
        MaxPainCalculator.MaxPainResult result = MaxPainCalculator.compute(chain);
        assertEquals(24000_00L, result.strikePaisa());
    }

    @Test
    void symmetricChain_returnsMiddleStrike() {
        List<OptionChainEntry> entries = List.of(
                new OptionChainEntry(23000_00L, quote(500), quote(500)),
                new OptionChainEntry(24000_00L, quote(500), quote(500)),
                new OptionChainEntry(25000_00L, quote(500), quote(500))
        );
        OptionChainSnapshot chain = new OptionChainSnapshot(NIFTY, LocalDate.now(), 24000_00L, entries);
        MaxPainCalculator.MaxPainResult result = MaxPainCalculator.compute(chain);
        assertEquals(24000_00L, result.strikePaisa(),
                "Symmetric OI should produce middle strike as max pain");
    }

    @Test
    void heavyCallOi_pullsMaxPainUp() {
        List<OptionChainEntry> entries = List.of(
                new OptionChainEntry(23000_00L, quote(10000), quote(100)),
                new OptionChainEntry(24000_00L, quote(5000), quote(100)),
                new OptionChainEntry(25000_00L, quote(100), quote(100))
        );
        OptionChainSnapshot chain = new OptionChainSnapshot(NIFTY, LocalDate.now(), 24000_00L, entries);
        MaxPainCalculator.MaxPainResult result = MaxPainCalculator.compute(chain);
        assertTrue(result.strikePaisa() >= 23000_00L,
                "Heavy call OI at lower strikes should pull max pain towards them");
    }

    @Test
    void heavyPutOi_pullsMaxPainDown() {
        List<OptionChainEntry> entries = List.of(
                new OptionChainEntry(23000_00L, quote(100), quote(100)),
                new OptionChainEntry(24000_00L, quote(100), quote(5000)),
                new OptionChainEntry(25000_00L, quote(100), quote(10000))
        );
        OptionChainSnapshot chain = new OptionChainSnapshot(NIFTY, LocalDate.now(), 24000_00L, entries);
        MaxPainCalculator.MaxPainResult result = MaxPainCalculator.compute(chain);
        assertTrue(result.strikePaisa() <= 25000_00L,
                "Heavy put OI at higher strikes should pull max pain towards them");
    }
}
