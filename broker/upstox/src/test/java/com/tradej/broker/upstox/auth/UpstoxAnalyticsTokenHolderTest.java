package com.tradej.broker.upstox.auth;

import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxAnalyticsTokenHolderTest {

    @Test
    void exposesAnalyticsTokenAndExpiry() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJleHAiOjE4MTE2MjgwMDB9"
                + ".signature";
        UpstoxConnectionSettings settings = new UpstoxConnectionSettings(
                "client", "secret", "http://127.0.0.1:18080/callback",
                null, null, jwt, true, false, 18080, 1_800_000L, 600_000L
        );
        UpstoxAnalyticsTokenHolder holder = new UpstoxAnalyticsTokenHolder(settings);
        assertEquals(jwt, holder.bearerToken());
        assertTrue(holder.analyticsOnly());
        assertEquals(1_811_628_000_000L, holder.expiryEpochMs());
        holder.ensureValid();
    }

    @Test
    void rejectsMissingAnalyticsToken() {
        assertThrows(IllegalArgumentException.class, () -> new UpstoxConnectionSettings(
                "client", "secret", "http://127.0.0.1:18080/callback",
                null, null, null, true, false, 18080, 1_800_000L, 600_000L
        ));
    }

    // JWT eyJleHAiOjE4MTE2MjgwMDB9 decodes to expiryEpochMs = 1_811_628_000_000
    private static final long EXPIRY_MS = 1_811_628_000_000L;

    @Test
    void ensureValidDoesNotThrowBeforeExpiry() {
        Instant beforeExpiry = Instant.ofEpochMilli(EXPIRY_MS - 1);
        Clock clock = Clock.fixed(beforeExpiry, ZoneOffset.UTC);
        UpstoxConnectionSettings settings = settingsWithJwt();
        new UpstoxAnalyticsTokenHolder(settings, clock).ensureValid();
    }

    @Test
    void ensureValidThrowsWhenExpired() {
        Instant afterExpiry = Instant.ofEpochMilli(EXPIRY_MS + 1);
        Clock clock = Clock.fixed(afterExpiry, ZoneOffset.UTC);
        UpstoxConnectionSettings settings = settingsWithJwt();
        var holder = new UpstoxAnalyticsTokenHolder(settings, clock);
        assertThrows(IllegalStateException.class, holder::ensureValid);
    }

    private static UpstoxConnectionSettings settingsWithJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJleHAiOjE4MTE2MjgwMDB9"
                + ".signature";
        return new UpstoxConnectionSettings(
                "client", "secret", "http://127.0.0.1:18080/callback",
                null, null, jwt, true, false, 18080, 1_800_000L, 600_000L
        );
    }
}
