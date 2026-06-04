package com.tradej.broker.upstox.auth;

import com.tradej.broker.upstox.config.UpstoxConnectionSettings;

import java.time.Clock;
import java.time.Instant;

/**
 * Long-lived Upstox analytics token (read-only APIs, no OAuth refresh).
 */
public final class UpstoxAnalyticsTokenHolder implements UpstoxBearerTokenSource {

    private final String token;
    private final long expiryEpochMs;
    private final Clock clock;

    public UpstoxAnalyticsTokenHolder(UpstoxConnectionSettings settings) {
        this(settings, Clock.systemUTC());
    }

    UpstoxAnalyticsTokenHolder(UpstoxConnectionSettings settings, Clock clock) {
        if (!settings.analyticsOnly()) {
            throw new IllegalArgumentException("UpstoxAnalyticsTokenHolder requires analyticsOnly=true");
        }
        String analyticsToken = settings.analyticsToken();
        if (analyticsToken == null || analyticsToken.isBlank()) {
            throw new IllegalArgumentException("upstox analytics token is required when analyticsOnly=true");
        }
        this.token = analyticsToken;
        this.expiryEpochMs = UpstoxJwtExpiry.parseExpiryEpochMs(analyticsToken);
        this.clock = clock;
    }

    @Override
    public String bearerToken() {
        return token;
    }

    @Override
    public void ensureValid() {
        if (expiryEpochMs > 0 && clock.millis() >= expiryEpochMs) {
            throw new IllegalStateException(
                    "Upstox analytics token expired at " + Instant.ofEpochMilli(expiryEpochMs)
                            + " — regenerate from Developer Apps → Analytics tab");
        }
    }

    @Override
    public long expiryEpochMs() {
        return expiryEpochMs;
    }

    @Override
    public boolean analyticsOnly() {
        return true;
    }
}
