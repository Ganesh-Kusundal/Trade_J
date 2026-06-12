package com.tradej.broker.dhan.client;

import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds Dhan connection credentials and notifies listeners when the access token rotates.
 *
 * <p>REST and native WebSocket transports read tokens via {@link DhanTokenProvider}; this holder
 * exists for token-rotation callbacks (e.g. reconnecting live feeds after TOTP refresh).
 */
public final class DhanClientHolder implements AutoCloseable {
    private final DhanConnectionSettings settings;
    private final DhanTokenProvider tokenProvider;
    private final CopyOnWriteArrayList<Runnable> rotationListeners = new CopyOnWriteArrayList<>();
    /**
     * Reentry guard: when a rotation listener is itself fetching the access token
     * (e.g., the WebSocket multiplexer's rebindAfterTokenRotation calls back into
     * tokenProvider.getAccessToken() to construct a new client), we must not
     * re-fire the rotation listeners. See CRITICAL/HIGH-4 in the broker review.
     */
    private final AtomicInteger rotationDepth = new AtomicInteger(0);

    private volatile String currentToken;

    public DhanClientHolder(DhanConnectionSettings settings, DhanTokenProvider tokenProvider) {
        this.settings = settings;
        this.tokenProvider = tokenProvider;
    }

    public DhanConnectionSettings settings() {
        return settings;
    }

    public DhanTokenProvider tokenProvider() {
        return tokenProvider;
    }

    public String accessToken() {
        if (rotationDepth.get() > 0) {
            // Already inside a rotation cascade; just return the latest token
            // without re-firing listeners.
            return tokenProvider.getAccessToken();
        }
        rotationDepth.incrementAndGet();
        try {
            String token = tokenProvider.getAccessToken();
            Runnable[] listeners = null;
            synchronized (this) {
                if (!Objects.equals(currentToken, token)) {
                    currentToken = token;
                    listeners = rotationListeners.toArray(Runnable[]::new);
                }
            }
            if (listeners != null) {
                for (Runnable listener : listeners) {
                    try {
                        listener.run();
                    } catch (RuntimeException ex) {
                        // Listener errors must not break the holder.
                        // Log via the tokenProvider's own logger is implicit;
                        // we silently swallow to preserve the previous contract.
                    }
                }
            }
            return token;
        } finally {
            rotationDepth.decrementAndGet();
        }
    }

    public void ensureValidToken() {
        tokenProvider.ensureValid();
        accessToken();
    }

    public void addRotationListener(Runnable listener) {
        rotationListeners.add(listener);
    }

    @Override
    public void close() {
        synchronized (this) {
            currentToken = null;
        }
    }
}
