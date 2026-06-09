package com.tradej.persistence.replay;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.port.EventBus;
import com.tradej.pipeline.clock.EventTimestamps;
import com.tradej.pipeline.clock.VirtualClock;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public final class ReplayRunner implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ReplayRunner.class);

    private final ChronicleQueue queue;
    private final ExcerptTailer tailer;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final EventBus eventBus;
    private final VirtualClock virtualClock;
    private final ReplayStateManager stateManager;
    private final ReplayMetrics metrics;
    private final AtomicLong entriesRead = new AtomicLong();
    private volatile boolean closed;

    public ReplayRunner(Path queuePath, EventBus eventBus) {
        this(queuePath, eventBus, null, ReplayStateManager.NOOP);
    }

    public ReplayRunner(Path queuePath, EventBus eventBus, VirtualClock virtualClock) {
        this(queuePath, eventBus, virtualClock, ReplayStateManager.NOOP);
    }

    public ReplayRunner(Path queuePath, EventBus eventBus, VirtualClock virtualClock, ReplayStateManager stateManager) {
        this.queue = ChronicleQueue.singleBuilder(queuePath.toFile()).build();
        this.tailer = queue.createTailer();
        this.eventBus = eventBus;
        this.virtualClock = virtualClock;
        this.stateManager = stateManager != null ? stateManager : ReplayStateManager.NOOP;
        this.metrics = new ReplayMetrics();
    }

    public ReplayMetrics metrics() {
        return metrics;
    }

    /**
     * Replay all events of the given type from the Chronicle Queue.
     * Returns a {@link ReplayResult} with observable counters so callers can
     * detect silent data loss (corrupt entries) or unexpected event types.
     *
     * <p>Entries are filtered by the type-discriminator envelope
     * ({@code eventType} field) so that only matching events are replayed.
     *
     * @param eventType the specific DomainEvent subclass to deserialize
     * @return result summary with counters for total, replayed, skipped, and failed entries
     */
    public ReplayResult replayAll(Class<? extends DomainEvent> eventType) {
        stateManager.beforeReplay();
        long totalRead = 0L;
        long replayed = 0L;
        long skipped = 0L;
        long failed = 0L;

        String raw;
        while ((raw = tailer.readText()) != null) {
            totalRead++;
            entriesRead.incrementAndGet();
            try {
                JsonNode envelope = objectMapper.readTree(raw);
                String storedType = envelope.path("eventType").asText(null);
                String expectedType = eventType.getSimpleName();

                if (storedType == null) {
                    // Legacy format without envelope — try direct deserialization
                    DomainEvent event = objectMapper.readValue(raw, eventType);
                    syncReplayClock(event);
                    eventBus.publish(event);
                    replayed++;
                    continue;
                }

                if (!storedType.equals(expectedType)) {
                    skipped++;
                    if (log.isDebugEnabled()) {
                        log.debug("Skipping entry of type {} (looking for {})", storedType, expectedType);
                    }
                    continue;
                }

                JsonNode eventNode = envelope.get("event");
                if (eventNode == null) {
                    failed++;
                    log.warn("Envelope missing 'event' field for type {}", storedType);
                    continue;
                }

                DomainEvent event = objectMapper.treeToValue(eventNode, eventType);
                syncReplayClock(event);
                eventBus.publish(event);
                replayed++;
            } catch (Exception ex) {
                failed++;
                if (log.isDebugEnabled()) {
                    log.debug("Failed to replay entry {}: {}", totalRead, ex.getMessage());
                }
            }
        }

        stateManager.afterReplay();
        ReplayResult result = new ReplayResult(totalRead, replayed, skipped, failed);
        metrics.recordReplay(replayed, failed, 0);
        log.info("Replay complete: {}", result.summary());
        return result;
    }

    private void syncReplayClock(DomainEvent event) {
        if (virtualClock == null || virtualClock.getMode() != VirtualClock.Mode.REPLAY) {
            return;
        }
        virtualClock.advanceVirtualTimeMs(EventTimestamps.exchangeOrEventTimeMs(event));
    }

    // ── Tail lag monitoring ──

    /**
     * Returns the approximate number of entries behind the tailer is relative to
     * the end of the queue. Useful for monitoring replay catch-up progress.
     *
     * <p>The lag is estimated by comparing the tailer's current index against a
     * temporary tailer positioned at the end. Chronicle Queue indices are opaque
     * longs that include cycle and offset; this method returns the delta only,
     * which approximates the number of unread entries when both tailers are on
     * the same cycle.
     *
     * @return approximate number of entries remaining to replay, or 0 if caught up
     */
    public long tailLag() {
        if (closed) {
            return 0L;
        }
        long current = tailer.index();
        if (current == 0L) {
            // Tailer hasn't read anything yet — lag is total queue size
            return 0L; // unknown at this point
        }
        ExcerptTailer endTailer = queue.createTailer().toEnd();
        long last = endTailer.index();
        if (last <= current) {
            return 0L;
        }
        // Chronicle Queue indices encode cycle + offset; the raw delta
        // approximates the number of entries between positions.
        return last - current;
    }

    /**
     * Current index position of the replay tailer.
     * Callers can sample this over time to track replay progress.
     */
    public long currentIndex() {
        return tailer.index();
    }

    /** Number of entries read so far (incremented per iteration). */
    public long entriesRead() {
        return entriesRead.get();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        queue.close();
    }
}
