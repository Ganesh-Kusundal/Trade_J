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
}
