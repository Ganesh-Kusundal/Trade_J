package com.tradej.gateway.router;

import com.tradej.gateway.protocol.GatewayBinaryCodec;
import com.tradej.gateway.protocol.GatewayTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Routes binary gateway messages to subscribed WebSocket sessions by topic.
 *
 * <p>Maintains a bidirectional mapping of topics to sessions and sessions to topics.
 * Each published message is encoded with a monotonically increasing sequence number.
 *
 * <p>Publishing is <em>non-blocking</em>: the caller enqueues a send task into a bounded
 * {@link BlockingQueue}. A dedicated background thread drains the queue and performs the
 * actual {@link WebSocketSession#sendMessage} calls. If the queue fills up under high
 * volatility, events are dropped with a WARN-level log and a dropped-event counter
 * (fixes GB-01 — prevents JVM memory exhaustion from unbounded WebSocket write buffers).
 */
public final class GatewayTopicRouter {

    private static final Logger log = LoggerFactory.getLogger(GatewayTopicRouter.class);

    /** Default maximum number of pending send tasks before backpressure drops. */
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    /** Maximum events to drain from the queue in a single batch. */
    private static final int BATCH_SIZE = 128;

    /** Poll timeout (ms) — keeps the drain loop responsive to shutdown signals. */
    private static final int POLL_TIMEOUT_MS = 100;

    /** Shutdown drain timeout in seconds. */
    private static final int STOP_TIMEOUT_SECONDS = 5;

    private final Map<GatewayTopic, Set<WebSocketSession>> topicSessions = new EnumMap<>(GatewayTopic.class);
    private final Map<String, Set<GatewayTopic>> sessionTopics = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    // ── Async backpressure infrastructure ──

    private final BlockingQueue<SendTask> sendQueue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong droppedEventCount = new AtomicLong();
    private final AtomicLong sentEventCount = new AtomicLong();
    private volatile Thread publisherThread;

    private static class SendTask {
        final GatewayTopic topic;
        final byte[] frame;

        SendTask(GatewayTopic topic, byte[] frame) {
            this.topic = topic;
            this.frame = frame;
        }
    }

    public GatewayTopicRouter() {
        this(DEFAULT_QUEUE_CAPACITY);
    }

    GatewayTopicRouter(int queueCapacity) {
        for (GatewayTopic topic : GatewayTopic.values()) {
            topicSessions.put(topic, new CopyOnWriteArraySet<>());
        }
        this.sendQueue = new ArrayBlockingQueue<>(queueCapacity);
    }

    /** Start the background publisher thread. */
    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread worker = new Thread(this::publishLoop, "gateway-publisher");
            worker.setDaemon(true);
            publisherThread = worker;
            worker.start();
            log.info("GatewayTopicRouter started queueCapacity={}", sendQueue.remainingCapacity() + sendQueue.size());
        }
    }

    /** Gracefully stop the background publisher thread. Drains remaining tasks. */
    public void stop() {
        running.set(false);
        Thread worker = publisherThread;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(STOP_TIMEOUT_SECONDS * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Drain any remaining tasks on shutdown
        drainRemaining();
        log.info("GatewayTopicRouter stopped sent={} dropped={}",
                sentEventCount.get(), droppedEventCount.get());
    }

    /**
     * Subscribe a WebSocket session to a topic.
     */
    public void subscribe(WebSocketSession session, GatewayTopic topic) {
        topicSessions.get(topic).add(session);
        sessionTopics.computeIfAbsent(session.getId(), id -> new CopyOnWriteArraySet<>()).add(topic);
    }

    /**
     * Unsubscribe a session from all topics.
     */
    public void unsubscribeAll(WebSocketSession session) {
        Set<GatewayTopic> topics = sessionTopics.remove(session.getId());
        if (topics == null) {
            return;
        }
        for (GatewayTopic topic : topics) {
            Set<WebSocketSession> sessions = topicSessions.get(topic);
            if (sessions != null) {
                sessions.remove(session);
            }
        }
    }

    /**
     * Non-blocking publish: encodes the payload into a frame and offers it to the
     * bounded send queue. If the queue is full, the event is dropped with a WARN-level
     * log and the dropped-event counter is incremented.
     *
     * <p>The actual {@link WebSocketSession#sendMessage} call happens on the background
     * publisher thread, keeping the caller (event dispatch thread) never blocked on I/O.
     */
    public void publish(GatewayTopic topic, byte[] payload) {
        byte[] frame = GatewayBinaryCodec.encode(topic, sequence.incrementAndGet(), payload);
        if (!sendQueue.offer(new SendTask(topic, frame))) {
            droppedEventCount.incrementAndGet();
            log.warn("Gateway send queue full — dropping event topic={} droppedTotal={}",
                    topic, droppedEventCount.get());
        }
    }

    /**
     * Publish to subscribers of the topic, filtered by session ID predicate.
     * Also non-blocking — delegates to {@link #publish(GatewayTopic, byte[])} without filtering
     * at the queue level; filtering is applied on the publisher thread when draining.
     */
    public void publishFiltered(GatewayTopic topic, byte[] payload, Predicate<String> sessionFilter) {
        if (sessionFilter == null) {
            publish(topic, payload);
            return;
        }
        // We attach the filter to the send task so the publisher thread can apply it
        byte[] frame = GatewayBinaryCodec.encode(topic, sequence.incrementAndGet(), payload);
        if (!sendQueue.offer(new FilteredSendTask(topic, frame, sessionFilter))) {
            droppedEventCount.incrementAndGet();
            log.warn("Gateway filtered send queue full — dropping event topic={} droppedTotal={}",
                    topic, droppedEventCount.get());
        }
    }

    /**
     * Return the number of subscribers for a topic.
     */
    public int subscriberCount(GatewayTopic topic) {
        Set<WebSocketSession> sessions = topicSessions.get(topic);
        return sessions == null ? 0 : sessions.size();
    }

    // ── Metrics ──

    /** Total events dropped due to send queue full. */
    public long droppedEventCount() {
        return droppedEventCount.get();
    }

    /** Total events successfully sent to WebSocket sessions. */
    public long sentEventCount() {
        return sentEventCount.get();
    }

    /** Current number of pending send tasks in the queue. */
    public int queueDepth() {
        return sendQueue.size();
    }

    // ── Private helpers ──

    /** Background loop: drain send tasks and dispatch to WebSocket sessions. */
    private void publishLoop() {
        List<SendTask> batch = new ArrayList<>(BATCH_SIZE);
        while (running.get() || !sendQueue.isEmpty()) {
            batch.clear();
            try {
                SendTask task = sendQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (task == null) {
                    continue; // timed out — loop back and check running flag
                }
                batch.add(task);
                sendQueue.drainTo(batch, BATCH_SIZE - 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            // Dispatch each task in the batch
            for (SendTask task : batch) {
                try {
                    dispatchToSessions(task);
                } catch (Exception e) {
                    log.debug("Gateway dispatch failed topic={}: {}", task.topic, e.getMessage());
                }
            }
        }
    }

    /** Send a single frame to all subscribers of the topic. */
    private void dispatchToSessions(SendTask task) {
        Set<WebSocketSession> sessions = topicSessions.get(task.topic);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        Predicate<String> filter = (task instanceof FilteredSendTask f) ? f.filter() : null;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            if (filter != null && !filter.test(session.getId())) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new BinaryMessage(task.frame));
                }
                sentEventCount.incrementAndGet();
            } catch (IOException e) {
                log.debug("Gateway send failed session={} topic={}: {}",
                        session.getId(), task.topic, e.getMessage());
            }
        }
    }

    /** Drain any remaining send tasks on shutdown. */
    private void drainRemaining() {
        List<SendTask> remaining = new ArrayList<>();
        sendQueue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            log.info("Draining {} remaining gateway send tasks on shutdown", remaining.size());
            for (SendTask task : remaining) {
                try {
                    dispatchToSessions(task);
                } catch (Exception e) {
                    log.debug("Gateway drain dispatch failed: {}", e.getMessage());
                }
            }
        }
    }

    /** A send task with an optional session filter. */
    private static final class FilteredSendTask extends SendTask {
        private final Predicate<String> filter;

        FilteredSendTask(GatewayTopic topic, byte[] frame, Predicate<String> filter) {
            super(topic, frame);
            this.filter = filter;
        }

        Predicate<String> filter() {
            return filter;
        }
    }
}
