package com.tradej.broker.core.reconnect;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notifies registered listeners after a broker WebSocket reconnects.
 */
public final class ReconnectListenerRegistry {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void notifyReconnect() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
