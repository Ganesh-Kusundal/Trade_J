package com.tradej.gateway.router;

import com.tradej.gateway.protocol.GatewayBinaryCodec;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.transport.WebSocketTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Routes binary gateway messages to subscribed WebSocket transports by topic.
 *
 * <p>Maintains a bidirectional mapping of topics to transports and transports to topics.
 * Each published message is encoded with a monotonically increasing sequence number.
 *
 * <p>Publishing is <em>non-blocking</em>: the caller enqueues a send task into a bounded
 * {@link BlockingQueue}. A dedicated background thread drains the queue and performs the
 * actual {@link WebSocketTransport#sendBinary} calls. If the queue fills up under high
 * volatility, events are dropped with a WARN-level log and a dropped-event counter
 * (fixes GB-01 — prevents JVM memory exhaustion from unbounded WebSocket write buffers).
 */
public final class GatewayTopicRouter {

    private static final Logger log = LoggerFactory.getLogger(GatewayTopicRouter.class);

    private static final int DEFAULT_QUEUE_CAPACITY = 1024;
    private static final int BATCH_SIZE = 128;
    private static final int POLL_TIMEOUT_MS = 100;
    private static final int STOP_TIMEOUT_SECONDS = 5;

    private final Map<GatewayTopic, Set<WebSocketTransport>> topicTransports = new EnumMap<>(GatewayTopic.class);
    private final Map<String, Set<GatewayTopic>> transportTopics = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

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
            topicTransports.put(topic, new CopyOnWriteArraySet<>());
        }
        this.sendQueue = new ArrayBlockingQueue<>(queueCapacity);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread worker = new Thread(this::publishLoop, "gateway-publisher");
            worker.setDaemon(true);
            publisherThread = worker;
            worker.start();
            log.info("GatewayTopicRouter started queueCapacity={}", sendQueue.remainingCapacity() + sendQueue.size());
        }
    }

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
        drainRemaining();
        log.info("GatewayTopicRouter stopped sent={} dropped={}",
                sentEventCount.get(), droppedEventCount.get());
    }

    public void subscribe(WebSocketTransport transport, GatewayTopic topic) {
        topicTransports.get(topic).add(transport);
        transportTopics.computeIfAbsent(transport.id(), id -> new CopyOnWriteArraySet<>()).add(topic);
    }

    public void unsubscribeAll(WebSocketTransport transport) {
        Set<GatewayTopic> topics = transportTopics.remove(transport.id());
        if (topics == null) {
            return;
        }
        for (GatewayTopic topic : topics) {
            Set<WebSocketTransport> transports = topicTransports.get(topic);
            if (transports != null) {
                transports.remove(transport);
            }
        }
    }

    public void publish(GatewayTopic topic, byte[] payload) {
        byte[] frame = GatewayBinaryCodec.encode(topic, sequence.incrementAndGet(), payload);
        if (!sendQueue.offer(new SendTask(topic, frame))) {
            droppedEventCount.incrementAndGet();
            log.warn("Gateway send queue full — dropping event topic={} droppedTotal={}",
                    topic, droppedEventCount.get());
        }
    }

    public void publishFiltered(GatewayTopic topic, byte[] payload, Predicate<String> sessionFilter) {
        if (sessionFilter == null) {
            publish(topic, payload);
            return;
        }
        byte[] frame = GatewayBinaryCodec.encode(topic, sequence.incrementAndGet(), payload);
        if (!sendQueue.offer(new FilteredSendTask(topic, frame, sessionFilter))) {
            droppedEventCount.incrementAndGet();
            log.warn("Gateway filtered send queue full — dropping event topic={} droppedTotal={}",
                    topic, droppedEventCount.get());
        }
    }

    public int subscriberCount(GatewayTopic topic) {
        Set<WebSocketTransport> transports = topicTransports.get(topic);
        return transports == null ? 0 : transports.size();
    }

    public long droppedEventCount() {
        return droppedEventCount.get();
    }

    public long sentEventCount() {
        return sentEventCount.get();
    }

    public int queueDepth() {
        return sendQueue.size();
    }

    private void publishLoop() {
        List<SendTask> batch = new ArrayList<>(BATCH_SIZE);
        while (running.get() || !sendQueue.isEmpty()) {
            batch.clear();
            try {
                SendTask task = sendQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (task == null) {
                    continue;
                }
                batch.add(task);
                sendQueue.drainTo(batch, BATCH_SIZE - 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            for (SendTask task : batch) {
                try {
                    dispatchToTransports(task);
                } catch (Exception e) {
                    log.debug("Gateway dispatch failed topic={}: {}", task.topic, e.getMessage());
                }
            }
        }
    }

    private void dispatchToTransports(SendTask task) {
        Set<WebSocketTransport> transports = topicTransports.get(task.topic);
        if (transports == null || transports.isEmpty()) {
            return;
        }
        Predicate<String> filter = (task instanceof FilteredSendTask f) ? f.filter() : null;
        for (WebSocketTransport transport : transports) {
            if (!transport.isOpen()) {
                continue;
            }
            if (filter != null && !filter.test(transport.id())) {
                continue;
            }
            try {
                transport.sendBinary(task.frame);
                sentEventCount.incrementAndGet();
            } catch (Exception e) {
                log.debug("Gateway send failed transport={} topic={}: {}",
                        transport.id(), task.topic, e.getMessage());
            }
        }
    }

    private void drainRemaining() {
        List<SendTask> remaining = new ArrayList<>();
        sendQueue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            log.info("Draining {} remaining gateway send tasks on shutdown", remaining.size());
            for (SendTask task : remaining) {
                try {
                    dispatchToTransports(task);
                } catch (Exception e) {
                    log.debug("Gateway drain dispatch failed: {}", e.getMessage());
                }
            }
        }
    }

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
