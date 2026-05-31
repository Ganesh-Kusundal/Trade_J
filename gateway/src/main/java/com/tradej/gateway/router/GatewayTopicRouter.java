package com.tradej.gateway.router;

import com.tradej.gateway.protocol.GatewayBinaryCodec;
import com.tradej.gateway.protocol.GatewayTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Routes binary gateway messages to subscribed WebSocket sessions by topic.
 * Maintains a bidirectional mapping of topics to sessions and sessions to topics.
 * Each published message is encoded with a monotonically increasing sequence number.
 */
public final class GatewayTopicRouter {

    private static final Logger log = LoggerFactory.getLogger(GatewayTopicRouter.class);

    private final Map<GatewayTopic, Set<WebSocketSession>> topicSessions = new EnumMap<>(GatewayTopic.class);
    private final Map<String, Set<GatewayTopic>> sessionTopics = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public GatewayTopicRouter() {
        for (GatewayTopic topic : GatewayTopic.values()) {
            topicSessions.put(topic, new CopyOnWriteArraySet<>());
        }
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
     * Publish a payload to all subscribers of the given topic.
     */
    public void publish(GatewayTopic topic, byte[] payload) {
        byte[] frame = GatewayBinaryCodec.encode(topic, sequence.incrementAndGet(), payload);
        Set<WebSocketSession> sessions = topicSessions.get(topic);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new BinaryMessage(frame));
                }
            } catch (IOException e) {
                log.debug("Gateway send failed session={} topic={}: {}", session.getId(), topic, e.getMessage());
            }
        }
    }

    /**
     * Publish to subscribers of the topic, filtered by session ID predicate.
     */
    public void publishFiltered(GatewayTopic topic, byte[] payload, Predicate<String> sessionFilter) {
        if (sessionFilter == null) {
            publish(topic, payload);
            return;
        }
        byte[] frame = GatewayBinaryCodec.encode(topic, sequence.incrementAndGet(), payload);
        Set<WebSocketSession> sessions = topicSessions.get(topic);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new BinaryMessage(frame));
                }
            } catch (IOException e) {
                log.debug("Gateway filtered send failed session={}: {}", session.getId(), e.getMessage());
            }
        }
    }

    /**
     * Return the number of subscribers for a topic.
     */
    public int subscriberCount(GatewayTopic topic) {
        Set<WebSocketSession> sessions = topicSessions.get(topic);
        return sessions == null ? 0 : sessions.size();
    }
}
