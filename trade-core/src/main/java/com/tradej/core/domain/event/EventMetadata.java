package com.tradej.core.domain.event;

import java.time.Instant;
import java.util.UUID;

public record EventMetadata(
        String eventId,
        long timestampMs,
        long timestampMonotonic,
        long sequenceId,
        String correlationId
) {
    public static EventMetadata root() {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli(),
                System.nanoTime(),
                0L,
                ""
        );
    }

    public static EventMetadata correlated(String correlationId, long sequenceId) {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli(),
                System.nanoTime(),
                sequenceId,
                correlationId == null ? "" : correlationId
        );
    }
}
