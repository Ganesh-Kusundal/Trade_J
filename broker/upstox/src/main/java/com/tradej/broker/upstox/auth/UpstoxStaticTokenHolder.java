package com.tradej.broker.upstox.auth;

import java.time.Instant;

/**
 * Fixed JWT token holder for configured access or analytics credentials.
 */
public final class UpstoxStaticTokenHolder implements UpstoxBearerTokenSource {

    private final String token;
    private final long expiryEpochMs;
    private final boolean analyticsOnly;
    private final String label;

    public UpstoxStaticTokenHolder(String token) {
        this(token, false, "Upstox token");
    }

    public UpstoxStaticTokenHolder(String token, boolean analyticsOnly, String label) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        this.token = token;
        this.expiryEpochMs = UpstoxJwtExpiry.parseExpiryEpochMs(token);
        this.analyticsOnly = analyticsOnly;
        this.label = label;
    }

    @Override
    public String bearerToken() {
        return token;
    }

    @Override
    public void ensureValid() {
        if (expiryEpochMs > 0 && System.currentTimeMillis() >= expiryEpochMs) {
            throw new IllegalStateException(
                    label + " expired at " + Instant.ofEpochMilli(expiryEpochMs));
        }
    }

    @Override
    public long expiryEpochMs() {
        return expiryEpochMs;
    }

    @Override
    public boolean analyticsOnly() {
        return analyticsOnly;
    }
}
