package com.tradej.gateway.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Batches {@link MarketTickEvent} JSON payloads within a configurable time window
 * and sends them as a single JSON array to the gateway router.
 *
 * <p>Non-tick events are published immediately (no batching).
 *
 * <p>At 10K ticks/sec with a 2ms window, this reduces JSON serializations from
 * 10K/sec to ~500/sec — a 20× reduction in serialization overhead.
 *
 * <p>Thread-safe. Uses a lock-protected buffer flushed by a scheduled executor.
 */
public final class TickBatcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TickBatcher.class);

    private final GatewayTopicRouter router;
    private final ObjectMapper objectMapper;
    private final long batchWindowMs;
    private final int maxBatchSize;

    private final List<ObjectNode> buffer = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService flusher;

    public TickBatcher(GatewayTopicRouter router, ObjectMapper objectMapper, long batchWindowMs, int maxBatchSize) {
        this.router = router;
        this.objectMapper = objectMapper;
        this.batchWindowMs = batchWindowMs;
        this.maxBatchSize = maxBatchSize;
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tick-batch-flusher");
            t.setDaemon(true);
            return t;
        });
        this.flusher.scheduleAtFixedRate(this::flush, batchWindowMs, batchWindowMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Add a tick payload to the batch buffer. If the buffer reaches maxBatchSize,
     * flush immediately.
     */
    public void add(ObjectNode tickPayload) {
        lock.lock();
        try {
            buffer.add(tickPayload);
            if (buffer.size() >= maxBatchSize) {
                flushLocked();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Flush all buffered tick payloads as a single JSON array.
     */
    public void flush() {
        lock.lock();
        try {
            flushLocked();
        } finally {
            lock.unlock();
        }
    }

    private void flushLocked() {
        if (buffer.isEmpty()) return;

        try {
            ArrayNode array = objectMapper.createArrayNode();
            for (ObjectNode node : buffer) {
                array.add(node);
            }
            byte[] payload = objectMapper.writeValueAsBytes(array);
            router.publish(GatewayTopic.MARKET_TICK, payload);
        } catch (Exception e) {
            log.warn("Failed to flush tick batch ({} ticks): {}", buffer.size(), e.getMessage());
        } finally {
            buffer.clear();
        }
    }

    public int bufferSize() {
        lock.lock();
        try {
            return buffer.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        flush();
        flusher.shutdown();
        try {
            if (!flusher.awaitTermination(2, TimeUnit.SECONDS)) {
                flusher.shutdownNow();
            }
        } catch (InterruptedException e) {
            flusher.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
