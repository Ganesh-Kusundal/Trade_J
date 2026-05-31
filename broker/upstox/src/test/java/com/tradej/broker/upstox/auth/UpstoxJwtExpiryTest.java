package com.tradej.broker.upstox.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UpstoxJwtExpiryTest {

    @Test
    void parsesExpFromFixtureJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJleHAiOjE4MTE2MjgwMDB9"
                + ".signature";
        long expiry = UpstoxJwtExpiry.parseExpiryEpochMs(jwt);
        assertEquals(1_811_628_000_000L, expiry);
    }

    @Test
    void returnsNegativeOneForNonJwt() {
        assertEquals(-1L, UpstoxJwtExpiry.parseExpiryEpochMs("not-a-jwt"));
    }

    @Test
    void returnsNegativeOneForBlank() {
        assertEquals(-1L, UpstoxJwtExpiry.parseExpiryEpochMs("  "));
    }

    @Test
    void fixtureExpiryIsInFuture() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJleHAiOjE4MTE2MjgwMDB9"
                + ".signature";
        assertTrue(UpstoxJwtExpiry.parseExpiryEpochMs(jwt) > System.currentTimeMillis());
    }
}
