package com.tradej.persistence.chronicle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import net.openhft.chronicle.queue.ChronicleQueue;

import java.io.UncheckedIOException;
import java.nio.file.Path;

public final class ChronicleAuditLogWriter implements DomainEventHandler<DomainEvent>, AutoCloseable {
    private final ChronicleQueue queue;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChronicleAuditLogWriter(Path queuePath) {
        this.queue = ChronicleQueue.singleBuilder(queuePath.toFile()).build();
    }

    @Override
    public void onEvent(DomainEvent event) {
        try {
            queue.createAppender().writeText(objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new UncheckedIOException(new java.io.IOException("Failed to write Chronicle event", exception));
        }
    }

    @Override
    public void close() {
        queue.close();
    }
}
