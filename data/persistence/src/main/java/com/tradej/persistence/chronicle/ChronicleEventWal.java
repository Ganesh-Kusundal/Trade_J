package com.tradej.persistence.chronicle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.EventWriteAheadLog;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Chronicle Queue-backed Write-Ahead Log for domain events.
 *
 * <p>Events are persisted to durable storage <em>before</em> entering the
 * Disruptor ring buffer. On crash recovery, the WAL can replay events
 * that were persisted but not yet processed.
 *
 * <p>Usage:
 * <pre>
 *   ChronicleEventWal wal = new ChronicleEventWal(Path.of("runtime/chronicle/wal"));
 *   // Before publishing to Disruptor:
 *   wal.write(event);
 *   eventBus.publish(event);
 *
 *   // On recovery:
 *   wal.replay(event -> eventBus.publish(event));
 * </pre>
 */
public final class ChronicleEventWal implements EventWriteAheadLog, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ChronicleEventWal.class);

    private final ChronicleQueue queue;
    private final ObjectMapper mapper;
    private final AtomicLong writeCount = new AtomicLong();
    private final AtomicLong replayCount = new AtomicLong();

    public ChronicleEventWal(Path walPath) {
        this.queue = SingleChronicleQueueBuilder.single(walPath.toFile()).build();
        this.mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        log.info("ChronicleEventWal initialized at {}", walPath);
    }

    /**
     * Write an event to the WAL. Must be called before publishing to the event bus.
     *
     * @param event the domain event to persist
     */
    @Override
    public void write(DomainEvent event) {
        try {
            WalEntry entry = new WalEntry(
                    Instant.now().toEpochMilli(),
                    event.getClass().getName(),
                    event.eventId(),
                    mapper.writeValueAsString(event)
            );
            queue.createAppender().writeText(mapper.writeValueAsString(entry));
            writeCount.incrementAndGet();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write event to WAL", e);
        }
    }

    /**
     * Replay all events from the WAL, passing each to the given consumer.
     * Used for crash recovery to re-process events that were persisted but
     * not yet handled by subscribers.
     *
     * @param eventConsumer consumer that receives each replayed event
     * @return number of events replayed
     */
    public long replayRaw(Consumer<WalRecord> eventConsumer) {
        ExcerptTailer tailer = queue.createTailer();
        long count = 0;
        String text;
        while ((text = tailer.readText()) != null) {
            try {
                WalEntry entry = mapper.readValue(text, WalEntry.class);
                eventConsumer.accept(new WalRecord(
                        entry.timestampMs,
                        entry.eventClass,
                        entry.eventId,
                        entry.eventJson
                ));
                count++;
                replayCount.incrementAndGet();
            } catch (IOException e) {
                log.warn("Failed to deserialize WAL entry, skipping: {}", e.getMessage());
            }
        }
        log.info("WAL replay complete: {} events replayed", count);
        return count;
    }

    /**
     * Replay all events, deserializing them into DomainEvent instances.
     * Implements {@link EventWriteAheadLog#replay(Consumer)}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public long replay(Consumer<DomainEvent> eventConsumer) {
        return replayRaw(record -> {
            try {
                Class<? extends DomainEvent> clazz =
                        (Class<? extends DomainEvent>) Class.forName(record.eventClass());
                DomainEvent event = mapper.readValue(record.eventJson(), clazz);
                eventConsumer.accept(event);
            } catch (ClassNotFoundException e) {
                log.warn("Event class not found during replay: {}", record.eventClass());
            } catch (IOException e) {
                log.warn("Failed to deserialize event during replay: {}", e.getMessage());
            }
        });
    }

    /**
     * Drain all events from the WAL into a list. Useful for testing.
     */
    public List<WalRecord> drain() {
        List<WalRecord> records = new ArrayList<>();
        replayRaw(records::add);
        return records;
    }

    public long writeCount() {
        return writeCount.get();
    }

    public long replayCount() {
        return replayCount.get();
    }

    @Override
    public void close() {
        queue.close();
        log.info("ChronicleEventWal closed (writes={}, replays={})", writeCount.get(), replayCount.get());
    }

    /**
     * Raw WAL entry as stored in Chronicle Queue.
     */
    public record WalEntry(
            long timestampMs,
            String eventClass,
            String eventId,
            String eventJson
    ) {
    }

    /**
     * WAL record returned during replay.
     */
    public record WalRecord(
            long timestampMs,
            String eventClass,
            String eventId,
            String eventJson
    ) {
    }
}
