package com.tradej.scanner.testing;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.scan.AssetClass;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.model.ScanAsset;
import com.tradej.scanner.model.ScanContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test harness for scanner criterion development.
 * Simplifies construction of {@link ScanContext} instances and provides
 * assertion helpers for criterion evaluation.
 *
 * <p>Usage:
 * <pre>{@code
 * var harness = new ScannerTestHarness();
 * var ctx = harness.context("RELIANCE", 250000L, 5_000_000L);
 * harness.assertMatches(new VolumeSpikeCriterion(2.0, 1000), ctx);
 * }</pre>
 */
public final class ScannerTestHarness {

    private static final long DEFAULT_TIMESTAMP = 1_700_000_000_000L;

    /**
     * Build a ScanContext with a Quote (no candles, no options).
     */
    public ScanContext context(String symbol, long ltpPaisa, long volume) {
        return context(symbol, ltpPaisa, volume, List.of());
    }

    /**
     * Build a ScanContext with a Quote and intraday candles.
     */
    public ScanContext context(String symbol, long ltpPaisa, long volume, List<Candle> candles) {
        Instrument instrument = instrument(symbol);
        Quote quote = new Quote(
                instrument, ltpPaisa, ltpPaisa, ltpPaisa, ltpPaisa, ltpPaisa,
                volume, volume / 2, volume / 2, 0L, DEFAULT_TIMESTAMP
        );
        ScanAsset asset = new ScanAsset(instrument, AssetClass.EQUITY, symbol);
        return new ScanContext(asset, quote, null, candles);
    }

    /**
     * Build a ScanContext with candles but no quote.
     */
    public ScanContext contextWithCandles(String symbol, Candle... candles) {
        Instrument instrument = instrument(symbol);
        ScanAsset asset = new ScanAsset(instrument, AssetClass.EQUITY, symbol);
        return new ScanContext(asset, null, null, List.of(candles));
    }

    /**
     * Build a ScanContext with a Quote, candles, and option chain.
     */
    public ScanContext contextWithOptions(String symbol, long ltpPaisa, long volume,
                                          OptionChainSnapshot optionChain) {
        Instrument instrument = instrument(symbol);
        Quote quote = new Quote(
                instrument, ltpPaisa, ltpPaisa, ltpPaisa, ltpPaisa, ltpPaisa,
                volume, volume / 2, volume / 2, 0L, DEFAULT_TIMESTAMP
        );
        ScanAsset asset = new ScanAsset(instrument, AssetClass.EQUITY, symbol);
        return new ScanContext(asset, quote, optionChain, List.of());
    }

    /**
     * Evaluate a criterion against a context, returning both match and score.
     */
    public CriterionResult evaluate(ScanCriterion criterion, ScanContext context) {
        boolean matches = criterion.matches(context);
        double score = criterion.score(context);
        String reason = criterion.reason(context);
        return new CriterionResult(criterion.type(), matches, score, reason);
    }

    /**
     * Assert that the criterion matches the context.
     */
    public void assertMatches(ScanCriterion criterion, ScanContext context) {
        assertTrue(criterion.matches(context),
                "Expected criterion '" + criterion.type() + "' to match but it did not");
    }

    /**
     * Assert that the criterion does NOT match the context.
     */
    public void assertNotMatches(ScanCriterion criterion, ScanContext context) {
        assertFalse(criterion.matches(context),
                "Expected criterion '" + criterion.type() + "' NOT to match but it did");
    }

    /**
     * Assert that the criterion's score meets a minimum threshold.
     */
    public void assertScoreAtLeast(ScanCriterion criterion, ScanContext context, double minScore) {
        double score = criterion.score(context);
        assertTrue(score >= minScore,
                "Expected score >= " + minScore + " for '" + criterion.type() + "' but got " + score);
    }

    // ── Candle Factory ───────────────────────────────────────────

    /**
     * Create a test candle with OHLCV data.
     */
    public Candle candle(String symbol, long open, long high, long low, long close, long volume) {
        long now = System.currentTimeMillis();
        return new Candle(symbol, "5m", now - 300_000, now,
                open, high, low, close, volume, true, 0L, 0L);
    }

    // ── Pre-built Contexts ───────────────────────────────────────

    /**
     * Context with high volume (10M) — triggers volume spike criteria.
     */
    public ScanContext highVolumeContext(String symbol) {
        List<Candle> lowVolCandles = List.of(
                candle(symbol, 250000, 251000, 249000, 250500, 100_000),
                candle(symbol, 250500, 252000, 250000, 251500, 120_000),
                candle(symbol, 251500, 253000, 251000, 252500, 110_000)
        );
        return context(symbol, 253000L, 10_000_000L, lowVolCandles);
    }

    /**
     * Context with gap-up pattern (open > previous close by 3%).
     */
    public ScanContext gapUpContext(String symbol) {
        long prevClose = 250000L;
        long gapOpen = 257500L; // 3% gap up
        return context(symbol, gapOpen, 5_000_000L, List.of(
                candle(symbol, prevClose - 1000, prevClose, prevClose - 2000, prevClose, 200_000),
                candle(symbol, gapOpen, gapOpen + 2000, gapOpen - 500, gapOpen + 1500, 3_000_000)
        ));
    }

    /**
     * Context with zero volume — should not match volume criteria.
     */
    public ScanContext zeroVolumeContext(String symbol) {
        return context(symbol, 250000L, 0L);
    }

    // ── Internal ─────────────────────────────────────────────────

    private Instrument instrument(String symbol) {
        return new Instrument(
                symbol, symbol, Exchange.NSE, ExchangeSegment.NSE_EQ,
                "EQ", null, null, null, OptionType.UNKNOWN, 1L, 5L
        );
    }

    /**
     * Result of evaluating a criterion.
     */
    public record CriterionResult(String type, boolean matches, double score, String reason) {}
}
