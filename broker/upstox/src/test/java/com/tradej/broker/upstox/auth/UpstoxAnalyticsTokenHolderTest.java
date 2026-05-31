package com.tradej.broker.upstox.auth;

import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
