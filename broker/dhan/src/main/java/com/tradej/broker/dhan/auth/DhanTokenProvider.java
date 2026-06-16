package com.tradej.broker.dhan.auth;

public interface DhanTokenProvider {
    String getAccessToken();

    DhanTokenInfo getTokenInfo();

    void ensureValid();

    /**
     * Monotonic counter incremented each time a new token is generated.
     */
    default long tokenGenerationId() { return 0; }

    /**
     * Unconditionally invalidate the cached token.
     */
    default void invalidate() {}

    /**
     * Compare-and-swap invalidation: only clears the token if the current
     * {@link #tokenGenerationId()} matches {@code failedGenerationId}.
     *
     * @return {@code true} if the token was actually invalidated
     */
    default boolean invalidate(long failedGenerationId) { invalidate(); return true; }

    /**
     * Returns the number of seconds until the current token expires.
     * Returns 0 if the token is expired or invalid, and
     * {@link Long#MAX_VALUE} if the token never expires (e.g. STATIC mode).
     */
    default long tokenRemainingSeconds() {
        DhanTokenInfo info = getTokenInfo();
        if (!info.valid()) {
            return 0L;
        }
        if (info.expiryEpochMs() == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long remainingMs = info.expiryEpochMs() - System.currentTimeMillis();
        return Math.max(0L, remainingMs / 1000);
    }
}
