package com.tradej.broker.upstox.auth;

/**
 * Supplies a Bearer token for Upstox REST and WebSocket authorize calls.
 */
public interface UpstoxBearerTokenSource {

    String bearerToken();

    void ensureValid();

    /** Token expiry epoch millis, or {@code -1} if unknown. */
    default long expiryEpochMs() {
        return -1L;
    }

    default boolean analyticsOnly() {
        return false;
    }
}
