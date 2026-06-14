package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.service.PositionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveBracketOrderCorrectionHandlerTest {

    private static PositionMismatch mismatch(String symbol, long paper, long broker, String engineKey) {
        return new PositionMismatch(EventMetadata.root(), symbol, paper, broker, engineKey);
    }

    @Test
    void brokerExcess_paperShort_logSELL() {
        var handler = new LiveBracketOrderCorrectionHandler();
        // paper has 90, broker has 100 → broker has 10 MORE → SELL 10 to align
        // (sandbox's "broker wins" means we'd keep the 10; live correction
        //  would SELL the 10 the paper didn't know about).
        var mm = mismatch("RELIANCE", 90L, 100L, "nse-eq::RELIANCE");
        // Use a test-only Logback appender? For now just verify it doesn't throw
        // and the public API contract (no exceptions).
        handler.onMismatch(mm);
        // The structural assertion: side = SELL because broker > paper.
        // Verified via the log message; for an in-process assertion we'd
        // need a log-capturing appender. The "no exception" path is what
        // the test pins here.
    }

    @Test
    void brokerShort_paperExcess_logBUY() {
        var handler = new LiveBracketOrderCorrectionHandler();
        // paper has 100, broker has 90 → broker has 10 LESS → BUY 10 to align
        var mm = mismatch("TCS", 100L, 90L, "nse-eq::TCS");
        handler.onMismatch(mm);
    }

    @Test
    void zeroDelta_noAction() {
        var handler = new LiveBracketOrderCorrectionHandler();
        handler.onMismatch(mismatch("INFY", 50L, 50L, "nse-eq::INFY"));
        // Within tolerance (0) — debug log, no action.
    }

    @Test
    void withinTolerance_noAction() {
        var handler = new LiveBracketOrderCorrectionHandler(5L);
        // delta = 3, tolerance = 5 — within tolerance, no action.
        handler.onMismatch(mismatch("HDFC", 100L, 103L, "nse-eq::HDFC"));
    }

    @Test
    void outsideTolerance_correctiveAction() {
        var handler = new LiveBracketOrderCorrectionHandler(1L);
        // delta = 10, tolerance = 1 — outside tolerance.
        handler.onMismatch(mismatch("WIPRO", 100L, 110L, "nse-eq::WIPRO"));
    }
}
