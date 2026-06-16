package com.tradej.persistence.chronicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DomainEventHandler;
import net.openhft.chronicle.queue.ChronicleQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Writes all {@link DomainEvent}s to a Chronicle Queue with a type-discriminator
 * envelope so that replay can deserialize each entry to its concrete type.
 *
 * <p>Envelope format:
 * <pre>{"eventType":"MarketTickEvent","event":{...}}</pre>
 *
 * <p>This fixes RP-01 (Chronicle replay deserializes all events as same type).
 */
public final class ChronicleAuditLogWriter implements DomainEventHandler<DomainEvent>, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ChronicleAuditLogWriter.class);
    private final ChronicleQueue queue;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChronicleAuditLogWriter(Path queuePath) {
        this.queue = ChronicleQueue.singleBuilder(queuePath.toFile()).build();
    }

    @Override
    public void onEvent(DomainEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            String typeName = event.getClass().getSimpleName();
            String wrapped = "{\"eventType\":\"" + typeName + "\",\"event\":" + eventJson + "}";
            queue.createAppender().writeText(wrapped);
        } catch (Exception exception) {
            throw new UncheckedIOException(new java.io.IOException("Failed to write Chronicle event", exception));
        }
    }

    @Override
    public void close() {
        queue.close();
    }

    /**
     * Deletes Chronicle queue files older than the specified number of days,
     * preserving the currently active file.
     * Delegates to {@link ChronicleRetention#cleanupOldFiles}.
     *
     * @param retentionDays files older than this many days are deleted
     * @return number of files deleted
     */
    public int cleanupOldFiles(long retentionDays) {
        return ChronicleRetention.cleanupOldFiles(queue, retentionDays, log);
    }
}
