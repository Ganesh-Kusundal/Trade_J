package com.tradej.core.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event metadata — every {@link DomainEvent} carries one.
 * <p>
 * The {@code schemaVersion} field captures the event schema version at
 * creation time, enabling backward-compatible deserialization of persisted
 * events that may have been created under an older schema.
 *
 * @param schemaVersion Schema version of this event (defaults to 1;
 *                      0 means unset / pre-versioning and is treated as 1)
 */
public record EventMetadata(
        String eventId,
        long timestampMs,
        long timestampMonotonic,
        long sequenceId,
        String correlationId,
        int schemaVersion
) {
    /**
     * Compact constructor: default schemaVersion 1 when deserializing from
     * pre-versioning storage where the field is absent (0 / unset).
     */
    public EventMetadata {
        if (schemaVersion == 0) {
            schemaVersion = 1;
        }
    }

    public static EventMetadata root() {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli(),
                System.nanoTime(),
                0L,
                "",
                EventSchemaVersion.CURRENT
        );
    }

    public static EventMetadata correlated(String correlationId, long sequenceId) {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli(),
                System.nanoTime(),
                sequenceId,
                correlationId == null ? "" : correlationId,
                EventSchemaVersion.CURRENT
        );
    }
}
