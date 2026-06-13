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

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@link com.tradej.execution.reconcile.DriftAlerter}
 * behavior on the
 * {@link LiveBracketOrderCorrectionHandler} (B2 follow-up,
 * 2026-06-13).
 *
 * <p>Three contracts:
 * <ol>
 *   <li><b>High drift triggers alerter</b> — when {@code absDelta >
 *       alertThreshold}, the injected {@code DriftAlerter.alertDrift}
 *       is called exactly once with the mismatch + absDelta.</li>
 *   <li><b>Low drift does not trigger alerter</b> — when {@code absDelta
 *       <= alertThreshold} (but still above tolerance so the WOULD
 *       PLACE log line fires), the alerter is NOT called.</li>
 *   <li><b>Alerter exception is caught</b> — if the alerter throws, the
 *       handler does not propagate the exception and the structured
 *       WOULD PLACE log line still fires (a failing alerter does not
 *       break the reconciliation pass).</li>
 * </ol>
 */
class LiveBracketOrderCorrectionHandlerDriftAlerterTest {

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

    private static PositionMismatch mismatch(String symbol, long paper, long broker) {
        return new PositionMismatch(EventMetadata.root(), symbol, paper, broker, "nse-eq::" + symbol);
    }

    @Test
    void highDrift_triggersAlerter() {
        RecordingDriftAlerter alerter = new RecordingDriftAlerter();
        var handler = new LiveBracketOrderCorrectionHandler(alerter, 1L, 100L);
        // absDelta = 200, threshold = 100 → alerter should fire
        handler.onMismatch(mismatch("RELIANCE", 100L, 300L));

        assertEquals(1, alerter.invocations(), "Alerter must be called exactly once for high drift");
        assertEquals(200L, alerter.lastAbsDelta(), "Alerter must receive the absolute delta");

        // The structured WOULD PLACE log line should also have fired.
        boolean hasWouldPlace = appender.list.stream()
                .anyMatch(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"));
        assertTrue(hasWouldPlace, "WOULD PLACE log must still fire alongside alerter");
    }

    @Test
    void lowDrift_doesNotTriggerAlerter() {
        RecordingDriftAlerter alerter = new RecordingDriftAlerter();
        var handler = new LiveBracketOrderCorrectionHandler(alerter, 1L, 100L);
        // absDelta = 50, threshold = 100 → alerter should NOT fire,
        // but absDelta > tolerance (1) so WOULD PLACE log still fires.
        handler.onMismatch(mismatch("TCS", 100L, 150L));

        assertEquals(0, alerter.invocations(), "Alerter must not fire for low drift");
        boolean hasWouldPlace = appender.list.stream()
                .anyMatch(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"));
        assertTrue(hasWouldPlace, "WOULD PLACE log must fire for any drift above tolerance");
    }

    @Test
    void alerterException_isCaught() {
        ThrowingDriftAlerter alerter = new ThrowingDriftAlerter();
        var handler = new LiveBracketOrderCorrectionHandler(alerter, 1L, 100L);
        // absDelta = 200, threshold = 100 → alerter would fire and throw

        // The handler must NOT propagate the exception.
        try {
            handler.onMismatch(mismatch("HDFC", 100L, 300L));
        } catch (RuntimeException e) {
            throw new AssertionError("Handler must swallow alerter exceptions", e);
        }

        assertEquals(1, alerter.invocations(), "Alerter must be called once even though it throws");

        // The structured WOULD PLACE log must still fire (alerter
        // failure does not break the reconciliation pass).
        boolean hasWouldPlace = appender.list.stream()
                .anyMatch(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"));
        assertTrue(hasWouldPlace, "WOULD PLACE log must still fire even when alerter throws");

        // The handler should have logged the alerter failure at ERROR.
        boolean hasErrorLog = appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.ERROR
                        && e.getFormattedMessage().contains("DriftAlerter.alertDrift failed"));
        assertTrue(hasErrorLog, "Handler must log alerter failure at ERROR");
    }

    @Test
    void nullAlerter_fallsBackToLogging() {
        // Defensive: passing null alerter must fall back to the
        // always-available LoggingDriftAlerter. The handler must
        // not NPE.
        var handler = new LiveBracketOrderCorrectionHandler(null, 1L, 100L);
        try {
            handler.onMismatch(mismatch("INFY", 100L, 300L));
        } catch (RuntimeException e) {
            throw new AssertionError("Null-alerter fallback must not throw", e);
        }
        // WOULD PLACE log should still fire (LoggingDriftAlerter is the fallback).
        boolean hasWouldPlace = appender.list.stream()
                .anyMatch(e -> e.getMessage().startsWith("LIVE_BRACKET_CORRECTION_WOULD_PLACE"));
        assertTrue(hasWouldPlace, "WOULD PLACE log must fire with null-alerter fallback");
    }

    // ── Test doubles ──

    private static final class RecordingDriftAlerter implements DriftAlerter {
        private final AtomicInteger invocations = new AtomicInteger();
        private volatile long lastAbsDelta;

        @Override
        public void alertDrift(PositionMismatch mismatch, long absDelta) {
            invocations.incrementAndGet();
            this.lastAbsDelta = absDelta;
        }

        int invocations() { return invocations.get(); }
        long lastAbsDelta() { return lastAbsDelta; }
    }

    private static final class ThrowingDriftAlerter implements DriftAlerter {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public void alertDrift(PositionMismatch mismatch, long absDelta) {
            invocations.incrementAndGet();
            throw new RuntimeException("simulated Slack webhook failure");
        }

        int invocations() { return invocations.get(); }
    }
}
