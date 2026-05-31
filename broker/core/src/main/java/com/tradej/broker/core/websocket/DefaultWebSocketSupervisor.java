package com.tradej.broker.core.websocket;

import com.tradej.broker.api.websocket.WebSocketSupervisor;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Default {@link WebSocketSupervisor} implementation.
 * <p>
 * Manages connection state transitions, message timestamp tracking,
 * staleness detection, and listener notification.
 * <p>
 * Thread-safe. State transitions are atomic via {@link AtomicReference}.
 */
public class DefaultWebSocketSupervisor implements WebSocketSupervisor {

    private final AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);
    private final List<Consumer<State>> stateListeners = new CopyOnWriteArrayList<>();
    private final long stalenessThresholdMs;

    private volatile long lastMessageTimestampMs = System.currentTimeMillis();
    private volatile URI currentUri;

    public DefaultWebSocketSupervisor(long stalenessThresholdMs) {
        this.stalenessThresholdMs = stalenessThresholdMs;
    }

    @Override
    public State state() {
        return state.get();
    }

    @Override
    public void connect(URI uri) {
        this.currentUri = uri;
        transitionTo(State.CONNECTING);
    }

    @Override
    public void disconnect() {
        transitionTo(State.DISCONNECTED);
    }

    @Override
    public void onMessage(ByteBuffer frame) {
        lastMessageTimestampMs = System.currentTimeMillis();
        // if was STALE, transition back to CONNECTED
        state.compareAndSet(State.STALE, State.CONNECTED);
    }

    @Override
    public void onClose(int code, String reason) {
        transitionTo(State.DISCONNECTED);
    }

    @Override
    public void onError(Throwable error) {
        if (state.get() == State.CONNECTED || state.get() == State.CONNECTING) {
            transitionTo(State.RECONNECTING);
        }
    }

    @Override
    public void sendHeartbeat() {
        // Subclasses override if the broker requires a specific heartbeat message.
    }

    @Override
    public long lastMessageTimestampMs() {
        return lastMessageTimestampMs;
    }

    @Override
    public long stalenessThresholdMs() {
        return stalenessThresholdMs;
    }

    @Override
    public void addStateListener(Consumer<State> listener) {
        stateListeners.add(listener);
    }

    @Override
    public void removeStateListener(Consumer<State> listener) {
        stateListeners.remove(listener);
    }

    /** Called by subclasses or the underlying WebSocket client when connection succeeds. */
    public void onConnected() {
        transitionTo(State.CONNECTED);
    }

    /** Checks if the feed is stale and transitions to STALE if so. */
    public void checkStaleness() {
        if (state.get() == State.CONNECTED
                && System.currentTimeMillis() - lastMessageTimestampMs > stalenessThresholdMs) {
            transitionTo(State.STALE);
        }
    }

    /** Returns the current URI, or {@code null} if not connected yet. */
    public URI currentUri() {
        return currentUri;
    }

    private void transitionTo(State newState) {
        State old = state.getAndSet(newState);
        if (old != newState) {
            stateListeners.forEach(l -> l.accept(newState));
        }
    }
}
