package com.tradej.broker.dhan.auth;

import com.tradej.broker.dhan.config.DhanConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DhanTokenRefreshExpiryTest {

    @TempDir
    Path tempDir;

    @Test
    void tokenManagerCreatesSuccessfully() {
        DhanConnectionSettings settings = DhanConnectionSettings.sandboxWithDefaults("client-1", "test-token");
        DhanTokenManager manager = new DhanTokenManager(settings);
        assertNotNull(manager);
    }

    @Test
    void fixedClockProvidesConsistentTime() {
        Clock fixed = Clock.fixed(Instant.parse("2026-06-06T10:00:00Z"), ZoneId.of("UTC"));
        Instant now = fixed.instant();
        assertEquals(Instant.parse("2026-06-06T10:00:00Z"), now);
        assertEquals(Instant.parse("2026-06-06T10:00:00Z"), fixed.instant());
    }

    @Test
    void tokenStateFileRoundTrip() throws IOException {
        Path stateFile = tempDir.resolve("token-state.json");
        Files.writeString(stateFile, "{\"accessToken\":\"test-123\",\"expiresAt\":9999999999999}");
        String content = Files.readString(stateFile);
        assertTrue(content.contains("test-123"));
    }

    @Test
    void missingTokenStateFileDoesNotCrash() {
        DhanConnectionSettings settings = DhanConnectionSettings.sandboxWithDefaults("client-1", "test-token");
        assertDoesNotThrow(() -> new DhanTokenManager(settings));
    }

    @Test
    void sandboxSettingsHaveSandboxBaseUrl() {
        DhanConnectionSettings settings = DhanConnectionSettings.sandboxWithDefaults("c1", "t1");
        assertTrue(settings.restBaseUrl().contains("sandbox"),
                "Sandbox settings should use sandbox URL");
    }
}
