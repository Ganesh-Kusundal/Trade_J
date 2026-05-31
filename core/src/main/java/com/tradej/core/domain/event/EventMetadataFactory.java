package com.tradej.core.domain.event;

import java.time.Clock;
import java.util.UUID;

/**
 * Creates {@link EventMetadata} using an injectable {@link Clock} for deterministic replay/backtest.
 */
public final class EventMetadataFactory {

    private final Clock clock;

    public EventMetadataFactory() {
        this(Clock.systemDefaultZone());
    }

    public EventMetadataFactory(Clock clock) {
        this.clock = clock;
    }

    public EventMetadata root() {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                clock.millis(),
                System.nanoTime(),
                0L,
                "",
                EventSchemaVersion.CURRENT
        );
    }

    public EventMetadata correlated(String correlationId, long sequenceId) {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                clock.millis(),
                System.nanoTime(),
                sequenceId,
                correlationId == null ? "" : correlationId,
                EventSchemaVersion.CURRENT
        );
    }

    /**
     * Create root metadata with an explicit schema version (for testing
     * migration scenarios or replaying events at a specific version).
     */
    public EventMetadata rootWithVersion(int schemaVersion) {
        return new EventMetadata(
                UUID.randomUUID().toString(),
                clock.millis(),
                System.nanoTime(),
                0L,
                "",
                schemaVersion
        );
    }
}
