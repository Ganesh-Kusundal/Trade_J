package com.tradej.persistence.chronicle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;

import java.io.UncheckedIOException;
import java.nio.file.Path;

public final class ChronicleAuditLogWriter implements DomainEventHandler<DomainEvent>, AutoCloseable {
    private final ChronicleQueue queue;
    private final ExcerptAppender appender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChronicleAuditLogWriter(Path queuePath) {
        this.queue = ChronicleQueue.singleBuilder(queuePath.toFile()).build();
        this.appender = queue.createAppender();
    }

    @Override
    public void onEvent(DomainEvent event) {
        try {
            appender.writeText(objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new UncheckedIOException(new java.io.IOException("Failed to write Chronicle event", exception));
        }
    }

    @Override
    public void close() {
        queue.close();
    }
}
