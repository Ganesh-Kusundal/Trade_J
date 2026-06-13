package com.tradej.execution.reconcile;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PositionMismatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the structural contract of the structured
 * {@code LIVE_BRACKET_CORRECTION_WOULD_PLACE} log message emitted by
 * {@link LiveBracketOrderCorrectionHandler}. This is the contract that
 * the OMS path subscribes to (today: human-readable; future: structured
 * log appender piped to the OMS).
 */
class LiveBracketOrderCorrectionHandlerLogContractTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger handlerLogger;

    @BeforeEach
    void attachAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(LiveBracketOrderCorrectionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void brokerExcess_logsSELLIntent() {
        var handler = new LiveBracketOrderCorrectionHandler();
        // paper=90, broker=100 → broker has 10 MORE → SELL 10
        handler.onMismatch(new PositionMismatch(
                EventMetadata.root(), "RELIANCE", 90L, 100L, "nse-eq::RELIANCE"));

        ILoggingEvent event = appender.list.stream()
                .filter(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"))
                .findFirst()
                .orElseThrow();
        assertEquals(Level.WARN, event.getLevel());
        String msg = event.getFormattedMessage();
        assertTrue(msg.contains("symbol=RELIANCE"), "symbol: " + msg);
        assertTrue(msg.contains("side=SELL"), "side: " + msg);
        assertTrue(msg.contains("qty=10"), "qty: " + msg);
        assertTrue(msg.contains("paperQty=90"), "paperQty: " + msg);
        assertTrue(msg.contains("brokerQty=100"), "brokerQty: " + msg);
        assertTrue(msg.contains("engineKey=nse-eq::RELIANCE"), "engineKey: " + msg);
        assertTrue(msg.contains("delta=10"), "delta: " + msg);
        assertTrue(msg.contains("tolerance=1"), "tolerance: " + msg);
    }

    @Test
    void brokerShort_logsBUYIntent() {
        var handler = new LiveBracketOrderCorrectionHandler();
        // paper=100, broker=90 → broker has 10 LESS → BUY 10
        handler.onMismatch(new PositionMismatch(
                EventMetadata.root(), "TCS", 100L, 90L, "nse-eq::TCS"));

        ILoggingEvent event = appender.list.stream()
                .filter(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"))
                .findFirst()
                .orElseThrow();
        String msg = event.getFormattedMessage();
        assertTrue(msg.contains("side=BUY"), "side: " + msg);
        assertTrue(msg.contains("qty=10"), "qty: " + msg);
        assertTrue(msg.contains("symbol=TCS"), "symbol: " + msg);
    }

    @Test
    void withinTolerance_noCorrectionLog() {
        var handler = new LiveBracketOrderCorrectionHandler(5L);
        // delta=3, tolerance=5 → within tolerance, NO WOULD PLACE log
        handler.onMismatch(new PositionMismatch(
                EventMetadata.root(), "HDFC", 100L, 103L, "nse-eq::HDFC"));

        boolean hasWouldPlace = appender.list.stream()
                .anyMatch(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"));
        assertTrue(!hasWouldPlace, "Within-tolerance drift must not emit WOULD PLACE");
    }

    @Test
    void zeroDelta_noCorrectionLog() {
        var handler = new LiveBracketOrderCorrectionHandler();
        handler.onMismatch(new PositionMismatch(
                EventMetadata.root(), "INFY", 50L, 50L, "nse-eq::INFY"));

        boolean hasWouldPlace = appender.list.stream()
                .anyMatch(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"));
        assertTrue(!hasWouldPlace, "Zero delta must not emit WOULD PLACE");
    }

    @Test
    void highTolerance_filtersSmallDrift() {
        var handler = new LiveBracketOrderCorrectionHandler(20L);
        // delta=10, tolerance=20 → within tolerance, NO WOULD PLACE log
        handler.onMismatch(new PositionMismatch(
                EventMetadata.root(), "WIPRO", 100L, 110L, "nse-eq::WIPRO"));

        boolean hasWouldPlace = appender.list.stream()
                .anyMatch(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"));
        assertTrue(!hasWouldPlace, "10-share drift must not fire under tolerance=20");
    }
}
