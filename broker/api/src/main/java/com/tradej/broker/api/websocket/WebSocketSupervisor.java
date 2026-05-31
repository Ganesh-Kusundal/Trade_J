package com.tradej.broker.api.websocket;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Contract for WebSocket lifecycle management.
 * <p>
 * Implementations manage connection state, heartbeats, reconnection, and staleness detection.
 * This is shared across all broker adapters — {@code trade-broker-core} provides a default
 * implementation using JDK {@code java.net.http.WebSocket}.
 * <p>
 * States flow: DISCONNECTED → CONNECTING → CONNECTED |
 *              CONNECTED → (error/close) → RECONNECTING | DISCONNECTED |
 *              CONNECTED → (no messages) → STALE → RECONNECTING | DISCONNECTED
 */
public interface WebSocketSupervisor extends AutoCloseable {

    /** Observable connection states. */
    enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        STALE,
        CLOSED
    }

    /** Returns the current state. Never blocks. */
    State state();

    /** Initiates connection. Returns immediately; use {@link #addStateListener} for completion. */
    void connect(URI uri);

    /** Gracefully disconnects with close frame. */
    void disconnect();

    /** Called by the underlying WebSocket client when a binary frame arrives. */
    void onMessage(ByteBuffer frame);

    /** Called by the underlying WebSocket client when the connection closes. */
    void onClose(int code, String reason);

    /** Called by the underlying WebSocket client when an error occurs. */
    void onError(Throwable error);

    /** Sends a heartbeat/ping. Implementations schedule this periodically. */
    void sendHeartbeat();

    /** Timestamp of the last received message (wall clock millis). */
    long lastMessageTimestampMs();

    /** Returns the staleness threshold in milliseconds. */
    long stalenessThresholdMs();

    /** Registers a listener for state transitions. */
    void addStateListener(Consumer<State> listener);

    /** Removes a previously registered state listener. */
    void removeStateListener(Consumer<State> listener);

    @Override
    default void close() {
        disconnect();
    }
}
