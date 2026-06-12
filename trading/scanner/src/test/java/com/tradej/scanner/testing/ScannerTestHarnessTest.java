package com.tradej.scanner.testing;

import com.tradej.scanner.criterion.VolumeSpikeCriterion;
import com.tradej.scanner.criterion.PctChangeFromOpenCriterion;
import com.tradej.scanner.model.ScanContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the ScannerTestHarness works correctly and demonstrates its usage.
 */
class ScannerTestHarnessTest {

    private final ScannerTestHarness harness = new ScannerTestHarness();

    @Test
    @DisplayName("context() builds ScanContext with quote and volume")
    void contextBuildsScanContextWithQuote() {
        ScanContext ctx = harness.context("RELIANCE", 250000L, 5_000_000L);

        assertTrue(ctx.hasValidQuote());
        assertEquals(250000L, ctx.quote().ltpPaisa());
        assertEquals(5_000_000L, ctx.quote().volume());
        assertEquals("RELIANCE", ctx.asset().instrument().symbol());
    }

    @Test
    @DisplayName("VolumeSpikeCriterion matches high volume context")
    void volumeSpikeMatchesHighVolume() {
        var criterion = new VolumeSpikeCriterion(2.0, 1000);
        var ctx = harness.highVolumeContext("RELIANCE");

        harness.assertMatches(criterion, ctx);
        harness.assertScoreAtLeast(criterion, ctx, 1000);
    }

    @Test
    @DisplayName("VolumeSpikeCriterion does NOT match zero volume context")
    void volumeSpikeDoesNotMatchZeroVolume() {
        var criterion = new VolumeSpikeCriterion(2.0, 1000);
        var ctx = harness.zeroVolumeContext("RELIANCE");

        harness.assertNotMatches(criterion, ctx);
    }

    @Test
    @DisplayName("evaluate() returns match and score together")
    void evaluateReturnsResult() {
        var criterion = new VolumeSpikeCriterion(2.0, 1000);
        var ctx = harness.highVolumeContext("RELIANCE");

        var result = harness.evaluate(criterion, ctx);

        assertTrue(result.matches());
        assertTrue(result.score() > 0);
        assertNotNull(result.reason());
        assertEquals("volume-spike", result.type());
    }

    @Test
    @DisplayName("candle() factory creates valid Candle")
    void candleFactoryWorks() {
        var candle = harness.candle("RELIANCE", 250000, 255000, 248000, 253000, 10000);

        assertEquals("RELIANCE", candle.symbol());
        assertEquals(250000, candle.openPaisa());
        assertEquals(255000, candle.highPaisa());
        assertEquals(248000, candle.lowPaisa());
        assertEquals(253000, candle.closePaisa());
        assertEquals(10000, candle.volume());
        assertTrue(candle.closed());
    }

    @Test
    @DisplayName("gapUpContext has open above previous close")
    void gapUpContextPattern() {
        var ctx = harness.gapUpContext("RELIANCE");

        assertTrue(ctx.hasValidQuote());
        assertTrue(ctx.intradayCandles().size() >= 2);
        // Last candle open (gap up) should be significantly above first candle close
        long prevClose = ctx.intradayCandles().get(0).closePaisa();
        long gapOpen = ctx.intradayCandles().get(1).openPaisa();
        assertTrue(gapOpen > prevClose, "Gap open " + gapOpen + " should exceed prev close " + prevClose);
    }
}
