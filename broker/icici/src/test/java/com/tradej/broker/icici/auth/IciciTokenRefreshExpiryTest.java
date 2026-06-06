package com.tradej.broker.icici.auth;

import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.config.IciciAuthMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class IciciTokenRefreshExpiryTest {

    @TempDir
    Path tempDir;

    @Test
    void tokenManagerCreatesWithStaticMode() throws IOException {
        Path tokenState = tempDir.resolve("token-state.json");
        BreezeConnectionSettings settings = BreezeConnectionSettings.withDefaults(
                "app-key", "secret-key", "static-token",
                IciciAuthMode.STATIC, null, null, null, null, tokenState,
                false, 5, 8080, "/callback", true, 30
        );
        BreezeTokenManager manager = new BreezeTokenManager(settings);
        assertNotNull(manager);
    }

    @Test
    void tokenManagerCreatesWithTotpMode() throws IOException {
        Path totpFile = tempDir.resolve("totp-secret.txt");
        Files.writeString(totpFile, "JBSWY3DPEHPK3PXP");
        Path tokenState = tempDir.resolve("token-state.json");
        BreezeConnectionSettings settings = BreezeConnectionSettings.withDefaults(
                "app-key", "secret-key", null,
                IciciAuthMode.TOTP_GENERATED, totpFile, null, null, null, tokenState,
                false, 5, 8080, "/callback", true, 30
        );
        BreezeTokenManager manager = new BreezeTokenManager(settings);
        assertNotNull(manager);
    }

    @Test
    void breezeSessionExpiryCheck() {
        long now = System.currentTimeMillis();
        BreezeSession expired = new BreezeSession("user", "key", "token",
                now - 3600_000, now - 60_000);
        assertTrue(expired.expiresAtEpochMs() < now);

        BreezeSession valid = new BreezeSession("user", "key", "token",
                now, now + 3600_000);
        assertTrue(valid.expiresAtEpochMs() > now);
    }

    @Test
    void breezeSessionPreservesFields() {
        long now = System.currentTimeMillis();
        BreezeSession session = new BreezeSession("user1", "key1", "token1",
                now, now + 3600_000);
        assertEquals("user1", session.userId());
        assertEquals("key1", session.sessionKey());
        assertEquals("token1", session.base64SessionToken());
    }
}
