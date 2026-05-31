package com.tradej.broker.dhan.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanConnectionSettingsUnitTest {
    @Test
    void withDefaultsUsesLiveEnvironmentAndBaseUrl() {
        DhanConnectionSettings settings = DhanConnectionSettings.withDefaults("cid", "tok");

        assertEquals(DhanApiEnvironment.LIVE, settings.environment());
        assertEquals("https://api.dhan.co/v2", settings.restBaseUrl());
        assertFalse(settings.isSandbox());
    }

    @Test
    void sandboxWithDefaultsUsesSandboxHost() {
        DhanConnectionSettings settings = DhanConnectionSettings.sandboxWithDefaults("cid", "tok");

        assertTrue(settings.isSandbox());
        assertEquals("https://sandbox.dhan.co/v2", settings.restBaseUrl());
    }

    @Test
    void normalizesExplicitBaseUrl() {
        DhanConnectionSettings settings = DhanConnectionSettings.withDefaults(
                "cid",
                "tok",
                DhanApiEnvironment.SANDBOX,
                "https://sandbox.dhan.co/v2/",
                DhanAuthMode.STATIC,
                null,
                null,
                null,
                10L
        );

        assertEquals("https://sandbox.dhan.co/v2", settings.restBaseUrl());
    }

    @Test
    void refreshBufferMillisDerivedFromMinutes() {
        DhanConnectionSettings settings = DhanConnectionSettings.liveWithDefaults("cid", "tok");
        assertEquals(600_000L, settings.refreshBufferMillis());
    }
}
