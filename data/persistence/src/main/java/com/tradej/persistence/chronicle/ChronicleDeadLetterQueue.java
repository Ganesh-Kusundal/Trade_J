package com.tradej.persistence.chronicle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.DeadLetterQueue;
import net.openhft.chronicle.queue.ChronicleQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chronicle Queue-backed dead-letter store for events dropped from bounded queues.
 */
public final class ChronicleDeadLetterQueue implements DeadLetterQueue, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ChronicleDeadLetterQueue.class);

    private final ChronicleQueue queue;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong appendCount = new AtomicLong();

    public ChronicleDeadLetterQueue(Path queuePath) {
        this.queue = ChronicleQueue.singleBuilder(queuePath.toFile()).build();
        log.info("ChronicleDeadLetterQueue initialized at {}", queuePath);
    }

    @Override
    public void append(String source, DomainEvent event, String reason) {
        try {
            String payload = mapper.writeValueAsString(new DlqEntry(
                    Instant.now().toEpochMilli(),
                    source,
                    event.getClass().getName(),
                    event.eventId(),
                    reason,
                    mapper.writeValueAsString(event)
            ));
            queue.createAppender().writeText(payload);
            long total = appendCount.incrementAndGet();
            log.warn("DLQ append source={} eventType={} eventId={} reason={} total={}",
                    source, event.getClass().getSimpleName(), event.eventId(), reason, total);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append to DLQ", e);
        }
    }

    public long appendCount() {
        return appendCount.get();
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

    @Override
    public void close() {
        queue.close();
    }

    record DlqEntry(
            long timestampMs,
            String source,
            String eventClass,
            String eventId,
            String reason,
            String eventJson
    ) {
    }
}
