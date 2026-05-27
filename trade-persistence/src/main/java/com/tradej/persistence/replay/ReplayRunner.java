package com.tradej.persistence.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.EventBus;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;

import java.nio.file.Path;

public final class ReplayRunner implements AutoCloseable {
    private final ChronicleQueue queue;
    private final ExcerptTailer tailer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventBus eventBus;

    public ReplayRunner(Path queuePath, EventBus eventBus) {
        this.queue = ChronicleQueue.singleBuilder(queuePath.toFile()).build();
        this.tailer = queue.createTailer();
        this.eventBus = eventBus;
    }

    public void replayAll(Class<? extends DomainEvent> eventType) {
        String raw;
        while ((raw = tailer.readText()) != null) {
            try {
                DomainEvent event = objectMapper.readValue(raw, eventType);
                eventBus.publish(event);
            } catch (Exception ignored) {
                // Replay is best-effort per event type because Chronicle may hold mixed event payloads.
            }
        }
    }

    @Override
    public void close() {
        queue.close();
    }
}
