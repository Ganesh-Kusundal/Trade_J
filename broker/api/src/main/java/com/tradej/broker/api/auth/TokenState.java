package com.tradej.broker.api.auth;

/**
 * Immutable snapshot of token state at a point in time.
 */
public record TokenState(
        String accessToken,
        String refreshToken,
        long expiryEpochMs,
        long issuedAtEpochMs,
        TokenSource source
) {
    /** Clock skew tolerance in milliseconds (30 seconds) */
    private static final long CLOCK_SKEW_TOLERANCE_MS = 30_000L;

    /** Returns {@code true} if the token has not yet expired (with clock skew tolerance). */
    public boolean valid() {
        return expiryEpochMs > System.currentTimeMillis() + CLOCK_SKEW_TOLERANCE_MS;
    }

    /** Returns {@code true} if the token should be refreshed soon (within the configured buffer). */
    public boolean refreshRecommended(long bufferMs) {
        long remaining = remainingMs();
        if (remaining <= 0) {
            return true;
        }
        long lifespan = expiryEpochMs - issuedAtEpochMs;
        if (lifespan > 0 && lifespan <= bufferMs) {
            // Short-lived tokens (lifespan shorter than refresh buffer) should not
            // trigger proactive refresh while still valid.
            return false;
        }
        return remaining < bufferMs;
    }

    /** Returns milliseconds until expiry (may be negative if already expired). */
    public long remainingMs() {
        return expiryEpochMs - System.currentTimeMillis();
    }
}
