package com.tradej.broker.api.auth;

/**
 * Unified contract for broker bearer-token sources.
 * <p>
 * All broker adapters (Dhan, Upstox, ICICI, future) expose tokens through this
 * single interface so that downstream components (HTTP clients, WebSocket
 * authorizers, observability) can hold a broker-agnostic reference.
 * <p>
 * Implementations must be thread-safe: every method may be called concurrently
 * from any thread, including the WebSocket I/O thread and the order REST thread.
 * <p>
 * Lifetime contract:
 * <ul>
 *   <li>{@link #bearerToken()} returns a non-null, non-blank token. It MAY
 *       block briefly to refresh if the current token has expired.</li>
 *   <li>{@link #ensureValid()} is a hint to the implementation that an
 *       outbound call is about to be made. Implementations should
 *       proactively refresh if the cached token is within their configured
 *       refresh window.</li>
 *   <li>{@link #invalidate()} forces the next call to {@code bearerToken()}
 *       to mint/acquire a fresh token. Used when the broker signals an
 *       invalid token (e.g., Dhan WebSocket close code 806).</li>
 *   <li>{@link #onRefresh(Runnable)} registers a callback invoked after every
 *       successful refresh or mint. Callbacks are invoked at most once per
 *       token change. Duplicate registration is idempotent.</li>
 *   <li>{@link #onInvalidate(Runnable)} registers a callback invoked when
 *       the token is force-invalidated. Used by WebSocket multiplexers to
 *       re-bind their connections with the new token.</li>
 * </ul>
 */
public interface BrokerTokenSource {

    /**
     * Returns a non-blank bearer token, refreshing synchronously if needed.
     *
     * @throws IllegalStateException if no token is available and a refresh
     *         attempt also failed
     */
    String bearerToken();

    /**
     * Ensures the cached token is valid for outbound use. Implementations
     * should refresh proactively based on their own buffer window.
     * <p>
     * This call is idempotent and cheap when the token is fresh.
     */
    void ensureValid();

    /**
     * Token expiry epoch millis, or {@code -1} if unknown (e.g., extended
     * read-only tokens with no {@code exp} claim).
     */
    long expiryEpochMs();

    /**
     * Force-invalidate the cached token. The next call to
     * {@link #bearerToken()} will mint/acquire a fresh token.
     * <p>
     * Default is a no-op. Implementations that have an in-memory cached
     * state (e.g., {@code DhanTokenManager}, {@code UpstoxTokenManager})
     * override to clear the cache. Registered {@link #onInvalidate(Runnable)}
     * callbacks are invoked before this method returns on those implementations.
     */
    default void invalidate() {
        // no-op default for stateless token sources (static/extended/analytics holders)
    }

    /**
     * Register a callback to be invoked after every successful token
     * refresh or initial acquisition.
     * <p>
     * Default is a no-op. Implementations backed by a listener registry
     * (e.g., {@code DhanTokenManager}, {@code UpstoxTokenManager}) override
     * to actually register the callback. Duplicate registration of the
     * same callback instance is idempotent in those implementations.
     *
     * @param callback the callback to invoke; must not be null
     */
    default void onRefresh(Runnable callback) {
        if (callback == null) return;
    }

    /**
     * Register a callback to be invoked when the token is force-invalidated
     * (either via {@link #invalidate()} or detected as invalid by the broker).
     * <p>
     * WebSocket multiplexers use this to re-bind their transport.
     * <p>
     * Default is a no-op. Implementations backed by a listener registry
     * override to actually register the callback.
     *
     * @param callback the callback to invoke; must not be null
     */
    default void onInvalidate(Runnable callback) {
        if (callback == null) return;
    }

    /**
     * Reusable, thread-safe listener registry. Use this nested type in concrete
     * implementations to satisfy {@link #onRefresh(Runnable)} and
     * {@link #onInvalidate(Runnable)}.
     * <p>
     * Listeners are deduplicated by reference identity. Listener exceptions
     * are caught and logged so that one bad listener cannot break the chain.
     */
    final class CallbackRegistry {
        private final java.util.Map<Runnable, Boolean> refreshListeners =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.Map<Runnable, Boolean> invalidateListeners =
                new java.util.concurrent.ConcurrentHashMap<>();

        public void onRefresh(Runnable callback) {
            if (callback == null) return;
            refreshListeners.put(callback, Boolean.TRUE);
        }

        public void onInvalidate(Runnable callback) {
            if (callback == null) return;
            invalidateListeners.put(callback, Boolean.TRUE);
        }

        public int refreshListenerCount() {
            return refreshListeners.size();
        }

        public int invalidateListenerCount() {
            return invalidateListeners.size();
        }

        public void fireRefresh() {
            for (Runnable listener : refreshListeners.keySet()) {
                try {
                    listener.run();
                } catch (RuntimeException ex) {
                    org.slf4j.LoggerFactory.getLogger(CallbackRegistry.class)
                            .warn("BrokerTokenSource onRefresh listener threw: {}", ex.getMessage());
                }
            }
        }

        public void fireInvalidate() {
            for (Runnable listener : invalidateListeners.keySet()) {
                try {
                    listener.run();
                } catch (RuntimeException ex) {
                    org.slf4j.LoggerFactory.getLogger(CallbackRegistry.class)
                            .warn("BrokerTokenSource onInvalidate listener threw: {}", ex.getMessage());
                }
            }
        }
    }
}
