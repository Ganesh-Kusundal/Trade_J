package com.tradej.broker.icici.auth;

public interface BreezeTokenProvider {
    void ensureValid();

    BreezeSession session();

    String appKey();

    String secretKey();

    /**
     * Monotonic counter incremented each time a new session is generated.
     * Used by HTTP clients to detect whether another thread already regenerated
     * the session before calling {@link #invalidate(long)}.
     */
    default long sessionGenerationId() { return 0; }

    /**
     * Unconditionally invalidate the cached session.
     * Prefer {@link #invalidate(long)} to avoid cascading regeneration.
     */
    default void invalidate() {}

    /**
     * Compare-and-swap invalidation: only clears the session if the current
     * {@link #sessionGenerationId()} matches {@code failedGenerationId}.
     *
     * @return {@code true} if the session was actually invalidated,
     *         {@code false} if another thread already regenerated it
     */
    default boolean invalidate(long failedGenerationId) { invalidate(); return true; }
}
