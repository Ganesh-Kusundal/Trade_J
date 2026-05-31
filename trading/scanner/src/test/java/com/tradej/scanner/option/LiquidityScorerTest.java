package com.tradej.scanner.option;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionGreeks;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class LiquidityScorerTest {

    @Test
    void higherOiAndVolumeScoresHigherWithTightSpread() {
        OptionQuote illiquid = quote(1000, 10, 10000, 10500);
        OptionQuote liquid = quote(50000, 5000, 10000, 10050);

        LiquidityScorer.ScoreResult low = LiquidityScorer.score(illiquid, 500, 0, 300, false);
        LiquidityScorer.ScoreResult high = LiquidityScorer.score(liquid, 500, 0, 300, false);

        assertTrue(high.passedFilters());
        assertTrue(high.score() > low.score());
    }

    @Test
    void rejectsWideSpreadWhenBidAskPresent() {
        OptionQuote wide = quote(10000, 1000, 10000, 12000);
        LiquidityScorer.ScoreResult result = LiquidityScorer.score(wide, 100, 0, 300, false);
        assertFalse(result.passedFilters());
    }

    @Test
    void strictSpreadRejectsMissingBidAsk() {
        OptionQuote noSpread = new OptionQuote(
                null, 15000, 10000, 1000, 0, 0, 0, 0, new OptionGreeks(null, null, null, null, null)
        );
        LiquidityScorer.ScoreResult relaxed = LiquidityScorer.score(noSpread, 100, 0, 300, false);
        LiquidityScorer.ScoreResult strict = LiquidityScorer.score(noSpread, 100, 0, 300, true);
        assertTrue(relaxed.passedFilters());
        assertFalse(strict.passedFilters());
    }

    private static OptionQuote quote(long oi, long volume, long bid, long ask) {
        Instrument instrument = new Instrument(
                "TEST", "TEST", Exchange.NSE, ExchangeSegment.NSE_FNO,
                "OPTION", "NIFTY", null, 2400000L, OptionType.CALL, 25, 5
        );
        return new OptionQuote(instrument, 10200, oi, volume, bid, 100, ask, 100, new OptionGreeks(null, null, null, null, null));
    }
}
