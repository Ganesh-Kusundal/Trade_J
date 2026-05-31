package com.tradej.app.health;

import com.tradej.core.domain.event.BrokerAdapterError;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BrokerErrorTrackerTest {

    private BrokerErrorTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new BrokerErrorTracker();
    }

    @Test
    void startsWithZeroErrors() {
        assertEquals(0L, tracker.totalErrors());
        assertTrue(tracker.errorsBySource().isEmpty());
        assertEquals(0L, tracker.lastErrorTimestampMs());
        assertEquals("", tracker.lastErrorSource());
        assertEquals("", tracker.lastErrorDetail());
    }

    @Test
    void countsBrokerAdapterError() {
        tracker.onEvent(new BrokerAdapterError(
                EventMetadata.root(), "dhan", "market-transport", "Connection refused"
        ));

        assertEquals(1L, tracker.totalErrors());
    }

    @Test
    void countsMultipleErrors() {
        tracker.onEvent(new BrokerAdapterError(
                EventMetadata.root(), "dhan", "market-transport", "Connection refused"
        ));
        tracker.onEvent(new BrokerAdapterError(
                EventMetadata.root(), "dhan", "order-auth", "Token expired"
        ));

        assertEquals(2L, tracker.totalErrors());
    }

    @Test
    void groupsErrorsBySource() {
        tracker.onEvent(new BrokerAdapterError(
                EventMetadata.root(), "dhan", "market-transport", "Connection refused"
        ));
        tracker.onEvent(new BrokerAdapterError(
                EventMetadata.root(), "dhan", "market-transport", "Timeout"
        ));
        tracker.onEvent(new BrokerAdapterError(
                EventMetadata.root(), "dhan", "order-auth", "Token expired"
        ));

        assertEquals(3L, tracker.totalErrors());
        assertEquals(2L, tracker.errorsBySource().get("market-transport"));
        assertEquals(1L, tracker.errorsBySource().get("order-auth"));
    }

    @Test
    void recordsLastErrorDetails() {
        tracker.onEvent(new BrokerAdapterError(
                EventMetadata.root(), "dhan", "market-transport", "Connection refused"
        ));

        assertTrue(tracker.lastErrorTimestampMs() > 0);
        assertEquals("market-transport", tracker.lastErrorSource());
        assertEquals("Connection refused", tracker.lastErrorDetail());
    }

    @Test
    void ignoresNonBrokerErrorEvents() {
        // TickReceived is not a BrokerAdapterError — should be ignored
        tracker.onEvent(new TickReceived(
                EventMetadata.root(), "SBIN", "1s", 150_00L, 100L, 10000L, System.currentTimeMillis(), null
        ));

        assertEquals(0L, tracker.totalErrors());
    }

    @Test
    void resetClearsAllState() {
        tracker.onEvent(new BrokerAdapterError(
                EventMetadata.root(), "dhan", "market-transport", "Connection refused"
        ));
        assertEquals(1L, tracker.totalErrors());

        tracker.reset();

        assertEquals(0L, tracker.totalErrors());
        assertTrue(tracker.errorsBySource().isEmpty());
        assertEquals(0L, tracker.lastErrorTimestampMs());
        assertEquals("", tracker.lastErrorSource());
        assertEquals("", tracker.lastErrorDetail());
    }

    @Test
    void errorsBySourceIsUnmodifiable() {
        tracker.onEvent(new BrokerAdapterError(
                EventMetadata.root(), "dhan", "market-transport", "Connection refused"
        ));

        assertThrows(UnsupportedOperationException.class, () ->
                tracker.errorsBySource().put("new-source", 99L)
        );
    }
}
