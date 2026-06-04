package com.tradej.broker.upstox.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxStaticTokenHolderTest {

    // JWT eyJleHAiOjE4MTE2MjgwMDB9 decodes to expiryEpochMs = 1_811_628_000_000
    private static final long EXPIRY_MS = 1_811_628_000_000L;

    private static final String VALID_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
            + ".eyJleHAiOjE4MTE2MjgwMDB9"
            + ".signature";

    @Test
    void ensureValidDoesNotThrowBeforeExpiry() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(EXPIRY_MS - 1), ZoneOffset.UTC);
        var holder = new UpstoxStaticTokenHolder(VALID_JWT, false, "test", clock);
        holder.ensureValid(); // should not throw
    }

    @Test
    void ensureValidThrowsWhenExpired() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(EXPIRY_MS + 1), ZoneOffset.UTC);
        var holder = new UpstoxStaticTokenHolder(VALID_JWT, false, "test", clock);
        assertThrows(IllegalStateException.class, holder::ensureValid);
    }

    @Test
    void ensureValidDoesNotThrowWhenExpiryIsZero() {
        // JWT without an exp claim — expiryEpochMs parses to 0, so ensureValid never throws
        String noExpiryJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.test";
        Clock clock = Clock.fixed(Instant.ofEpochMilli(Long.MAX_VALUE), ZoneOffset.UTC);
        var holder = new UpstoxStaticTokenHolder(noExpiryJwt, false, "test", clock);
        holder.ensureValid(); // should not throw even with max clock
    }

    @Test
    void exposesTokenMetadata() {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        var holder = new UpstoxStaticTokenHolder(VALID_JWT, true, "analytics", clock);
        assertEquals(VALID_JWT, holder.bearerToken());
        assertTrue(holder.analyticsOnly());
        assertEquals(EXPIRY_MS, holder.expiryEpochMs());
    }

    @Test
    void rejectsBlankToken() {
        assertThrows(IllegalArgumentException.class,
                () -> new UpstoxStaticTokenHolder("  "));
    }
}
