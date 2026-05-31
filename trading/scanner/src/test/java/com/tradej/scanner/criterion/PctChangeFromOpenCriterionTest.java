package com.tradej.scanner.criterion;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.scanner.model.AssetClass;
import com.tradej.scanner.model.ScanAsset;
import com.tradej.scanner.model.ScanContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PctChangeFromOpenCriterionTest {
    @Test
    void matchesWhenPctAboveThreshold() {
        Instrument instrument = new Instrument(
                "SBIN", "SBIN", Exchange.NSE, ExchangeSegment.NSE_EQ,
                "EQ", "SBIN", null, null, null, 1L, 5L
        );
        Quote quote = new Quote(instrument, 10200L, 10000L, 10300L, 9900L, 10100L, 1000L, 0L, 0L, 1L);
        ScanContext context = new ScanContext(
                new ScanAsset(instrument, AssetClass.EQUITY, "SBIN"),
                quote,
                null,
                null
        );
        ScanCriterion criterion = new PctChangeFromOpenCriterion(1.5, null);
        assertTrue(criterion.matches(context));
    }

    @Test
    void rejectsWhenBelowThreshold() {
        Instrument instrument = new Instrument(
                "SBIN", "SBIN", Exchange.NSE, ExchangeSegment.NSE_EQ,
                "EQ", "SBIN", null, null, null, 1L, 5L
        );
        Quote quote = new Quote(instrument, 10050L, 10000L, 10300L, 9900L, 10100L, 1000L, 0L, 0L, 1L);
        ScanContext context = new ScanContext(
                new ScanAsset(instrument, AssetClass.EQUITY, "SBIN"),
                quote,
                null,
                null
        );
        ScanCriterion criterion = new PctChangeFromOpenCriterion(1.5, null);
        assertFalse(criterion.matches(context));
    }
}
