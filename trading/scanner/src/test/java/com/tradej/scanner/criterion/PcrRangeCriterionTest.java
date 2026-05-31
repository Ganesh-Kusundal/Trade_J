package com.tradej.scanner.criterion;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.OptionQuote;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.scanner.model.AssetClass;
import com.tradej.scanner.model.ScanAsset;
import com.tradej.scanner.model.ScanContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PcrRangeCriterionTest {
    @Test
    void matchesWhenPcrInRange() {
        Instrument underlying = new Instrument(
                "NIFTY", "NIFTY", Exchange.NSE, ExchangeSegment.IDX_I,
                "INDEX", "NIFTY", null, null, null, 1L, 5L
        );
        OptionQuote call = new OptionQuote(
                underlying, 100L, 1000L, 10L, 0L, 0L, 0L, 0L, null
        );
        OptionQuote put = new OptionQuote(
                underlying, 100L, 1500L, 10L, 0L, 0L, 0L, 0L, null
        );
        OptionChainSnapshot chain = new OptionChainSnapshot(
                underlying,
                LocalDate.now().plusDays(7),
                2200000L,
                List.of(new OptionChainEntry(2200000L, call, put))
        );
        ScanContext context = new ScanContext(
                new ScanAsset(underlying, AssetClass.OPTION, "NIFTY"),
                null,
                chain,
                null
        );
        assertTrue(new PcrRangeCriterion(1.0, 2.0).matches(context));
    }
}
