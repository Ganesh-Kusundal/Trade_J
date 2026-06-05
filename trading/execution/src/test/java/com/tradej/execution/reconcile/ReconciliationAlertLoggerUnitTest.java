package com.tradej.execution.reconcile;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PositionMismatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ReconciliationAlertLoggerUnitTest {

    private ReconciliationAlertLogger handler;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        handler = new ReconciliationAlertLogger(null, false, 0L);

        // Capture log output from the handler's logger
        logger = (Logger) LoggerFactory.getLogger(ReconciliationAlertLogger.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAndStopAllAppenders();
    }

    @Test
    void logsMismatchAtWarnLevel() {
        var event = new PositionMismatch(
                EventMetadata.root(),
                "SBIN", 100L, 80L, "oms-reconcile"
        );

        handler.onEvent(event);

        List<ILoggingEvent> logs = listAppender.list;
        assertEquals(1, logs.size(), "Should produce exactly one log entry");

        ILoggingEvent log = logs.getFirst();
        assertEquals(Level.WARN, log.getLevel());
        assertTrue(log.getFormattedMessage().contains("Position mismatch detected"));
        assertTrue(log.getFormattedMessage().contains("SBIN"));
        assertTrue(log.getFormattedMessage().contains("expectedQuantity=100"));
        assertTrue(log.getFormattedMessage().contains("brokerQuantity=80"));
        assertTrue(log.getFormattedMessage().contains("oms-reconcile"));
    }

    @Test
    void logsMultipleMismatchesIndependently() {
        handler.onEvent(new PositionMismatch(
                EventMetadata.root(), "SBIN", 100L, 80L, "oms-reconcile"
        ));
        handler.onEvent(new PositionMismatch(
                EventMetadata.root(), "TCS", 50L, 0L, "broker-reconcile"
        ));

        assertEquals(2, listAppender.list.size());
        assertTrue(listAppender.list.get(0).getFormattedMessage().contains("SBIN"));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("TCS"));
    }

    @Test
    void logsZeroQuantityMismatch() {
        handler.onEvent(new PositionMismatch(
                EventMetadata.root(), "SBIN", 0L, 0L, "oms-reconcile"
        ));

        assertEquals(1, listAppender.list.size());
        assertEquals(Level.WARN, listAppender.list.getFirst().getLevel());
    }
}
