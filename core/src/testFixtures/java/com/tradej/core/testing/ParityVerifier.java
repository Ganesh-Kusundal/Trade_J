package com.tradej.core.testing;

import com.tradej.core.domain.event.DomainEvent;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Objects;

/**
 * Compares two event sequences for structural equality, ignoring
 * metadata timestamps and event IDs that are expected to differ.
 * <p>
 * Usage:
 * <pre>{@code
 * List<DomainEvent> liveEvents = ...;
 * List<DomainEvent> replayEvents = ...;
 * ParityResult result = ParityVerifier.compare(liveEvents, replayEvents);
 * result.assertMatch();
 * }</pre>
 */
public final class ParityVerifier {

    private ParityVerifier() {
    }

    /**
     * Compares two event sequences for structural parity.
     * Ignores: eventId, timestampMs, timestampMonotonic.
     * Compares: event type, all business fields.
     */
    public static ParityResult compare(List<? extends DomainEvent> live,
                                        List<? extends DomainEvent> replay) {
        if (live.size() != replay.size()) {
            return ParityResult.mismatch("Size mismatch: live=" + live.size() + " replay=" + replay.size());
        }

        for (int i = 0; i < live.size(); i++) {
            DomainEvent liveEvent = live.get(i);
            DomainEvent replayEvent = replay.get(i);

            if (!liveEvent.getClass().equals(replayEvent.getClass())) {
                return ParityResult.mismatch(
                        "Type mismatch at index " + i + ": "
                                + liveEvent.getClass().getSimpleName() + " vs "
                                + replayEvent.getClass().getSimpleName());
            }

            if (!eventsStructurallyEqual(liveEvent, replayEvent)) {
                return ParityResult.mismatch(
                        "Content mismatch at index " + i + " ("
                                + liveEvent.getClass().getSimpleName() + "): "
                                + "live=" + liveEvent + " replay=" + replayEvent);
            }
        }

        return ParityResult.match(live.size());
    }

    /**
     * Compares two events for structural equality, ignoring metadata identity fields.
     */
    public static boolean eventsStructurallyEqual(DomainEvent a, DomainEvent b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (!a.getClass().equals(b.getClass())) {
            return false;
        }

        // Compare business fields (everything except metadata)
        // For records, we can compare all components except metadata
        return deepEquals(a, b);
    }

    /**
     * Deep comparison of two event records, ignoring metadata fields.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean deepEquals(DomainEvent a, DomainEvent b) {
        // Compare via record canonical equals, then override metadata comparison
        if (a instanceof java.lang.Record ra && b instanceof java.lang.Record rb) {
            RecordComponent[] compA = getRecordComponents(ra);
            RecordComponent[] compB = getRecordComponents(rb);

            if (compA.length != compB.length) {
                return false;
            }

            for (int i = 0; i < compA.length; i++) {
                String name = compA[i].getName();
                // Skip metadata identity fields
                if ("metadata".equals(name)) {
                    continue;
                }
                try {
                    Object valA = compA[i].getAccessor().invoke(ra);
                    Object valB = compB[i].getAccessor().invoke(rb);
                    if (!Objects.deepEquals(valA, valB)) {
                        return false;
                    }
                } catch (ReflectiveOperationException e) {
                    return false;
                }
            }
            return true;
        }

        // Fallback: use equals (will include metadata, so may produce false negatives)
        return a.equals(b);
    }

    private static RecordComponent[] getRecordComponents(Record record) {
        return record.getClass().getRecordComponents();
    }

    /**
     * Result of a parity comparison.
     */
    public record ParityResult(boolean matched, String message, int comparedEvents) {

        static ParityResult match(int count) {
            return new ParityResult(true, "Matched " + count + " events", count);
        }

        static ParityResult mismatch(String message) {
            return new ParityResult(false, message, 0);
        }

        /**
         * Asserts that the comparison matched. Throws AssertionError if not.
         */
        public void assertMatch() {
            if (!matched) {
                throw new AssertionError("Parity mismatch: " + message);
            }
        }

        /**
         * Returns true if the comparison matched.
         */
        public boolean isMatch() {
            return matched;
        }
    }
}
