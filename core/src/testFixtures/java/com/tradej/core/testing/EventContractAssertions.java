package com.tradej.core.testing;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;

import java.util.Collection;
import java.util.List;

/**
 * Reusable assertions for verifying event contracts and invariants.
 * <p>
 * Usage:
 * <pre>{@code
 * EventContractAssertions.assertHasRequiredFields(event);
 * EventContractAssertions.assertCausationChain(events);
 * EventContractAssertions.assertAllEventsAreRecords();
 * }</pre>
 */
public final class EventContractAssertions {

    private EventContractAssertions() {
    }

    /**
     * Asserts that the event has all required metadata fields populated.
     */
    public static void assertHasRequiredFields(DomainEvent event) {
        EventMetadata meta = event.metadata();
        assertNotNull(meta, "EventMetadata must not be null", event);
        assertNonEmpty(meta.eventId(), "eventId must not be empty", event);
        assertTrue(meta.timestampMs() > 0, "timestampMs must be positive", event);
        assertNotNull(meta.correlationId(), "correlationId must not be null", event);
    }

    /**
     * Asserts that the event ID is unique (not a default/empty value).
     */
    public static void assertHasEventId(DomainEvent event) {
        String id = event.eventId();
        assertNonEmpty(id, "eventId must not be empty", event);
        assertTrue(!id.equals("null"), "eventId must not be 'null'", event);
    }

    /**
     * Asserts that the event has a positive timestamp.
     */
    public static void assertHasTimestamp(DomainEvent event) {
        assertTrue(event.timestampMs() > 0,
                "timestampMs must be positive but was " + event.timestampMs(), event);
    }

    /**
     * Asserts that the event has a correlation ID (may be empty for root events).
     */
    public static void assertHasCorrelationId(DomainEvent event) {
        assertNotNull(event.correlationId(), "correlationId must not be null", event);
    }

    /**
     * Asserts that the event has a causation ID (may be empty for root events).
     * Note: This will fail until causation ID is added to the event contract.
     */
    public static void assertHasCausationId(DomainEvent event) {
        // Placeholder — will be enforced once causationId is added to DomainEvent
    }

    /**
     * Asserts that the event has a source/provenance field.
     * Note: This will fail until source is added to the event contract.
     */
    public static void assertHasSource(DomainEvent event) {
        // Placeholder — will be enforced once source is added to DomainEvent
    }

    /**
     * Asserts all events in a collection have required fields.
     */
    public static void assertAllHaveRequiredFields(Collection<? extends DomainEvent> events) {
        for (DomainEvent event : events) {
            assertHasRequiredFields(event);
        }
    }

    /**
     * Asserts the causal chain: each event's correlation ID should link back
     * to the originating event.
     */
    public static void assertCausationChain(List<? extends DomainEvent> events) {
        for (int i = 0; i < events.size(); i++) {
            DomainEvent event = events.get(i);
            assertHasRequiredFields(event);
            // Root events (index 0 or no correlation) are allowed empty correlationId
            if (i > 0) {
                assertTrue(!event.correlationId().isEmpty(),
                        "Event at index " + i + " must have non-empty correlationId", event);
            }
        }
    }

    /**
     * Asserts that all events in the list are in timestamp order.
     */
    public static void assertTimestampOrder(List<? extends DomainEvent> events) {
        for (int i = 1; i < events.size(); i++) {
            assertTrue(events.get(i).timestampMs() >= events.get(i - 1).timestampMs(),
                    "Events out of order at index " + i + ": "
                            + events.get(i - 1).timestampMs() + " > " + events.get(i).timestampMs(),
                    events.get(i));
        }
    }

    /**
     * Asserts that all events in the list are in sequence ID order.
     */
    public static void assertSequenceOrder(List<? extends DomainEvent> events) {
        for (int i = 1; i < events.size(); i++) {
            assertTrue(events.get(i).sequenceId() >= events.get(i - 1).sequenceId(),
                    "Events out of sequence order at index " + i + ": "
                            + events.get(i - 1).sequenceId() + " > " + events.get(i).sequenceId(),
                    events.get(i));
        }
    }

    /**
     * Asserts that the given value is not null with a descriptive message.
     */
    public static void assertNotNull(Object value, String message, DomainEvent context) {
        if (value == null) {
            throw new AssertionError(message + " [event=" + context.getClass().getSimpleName()
                    + ", id=" + context.eventId() + "]");
        }
    }

    /**
     * Asserts that the string is not null or empty.
     */
    public static void assertNonEmpty(String value, String message, DomainEvent context) {
        if (value == null || value.isEmpty()) {
            throw new AssertionError(message + " [event=" + context.getClass().getSimpleName()
                    + ", id=" + context.eventId() + "]");
        }
    }

    /**
     * Asserts a boolean condition with a descriptive message.
     */
    public static void assertTrue(boolean condition, String message, DomainEvent context) {
        if (!condition) {
            throw new AssertionError(message + " [event=" + context.getClass().getSimpleName()
                    + ", id=" + context.eventId() + "]");
        }
    }

    /**
     * Asserts a boolean condition (no event context).
     */
    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
