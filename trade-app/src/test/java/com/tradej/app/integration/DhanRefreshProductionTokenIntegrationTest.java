package com.tradej.app.integration;

import com.tradej.broker.dhan.auth.DhanAuthClient;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regenerates the production {@code runtime/dhan-token-state.json} via TOTP when the cached token
 * no longer passes broker preflight. Run manually after live token expiry.
 */
@Tag("integration")
@Tag("broker-rest")
class DhanRefreshProductionTokenIntegrationTest {
    @Test
    void regeneratesProductionTokenStateViaTotp() throws Exception {
        DhanConnectionSettings settings = LiveDhanTestSupport.liveConnectionSettingsWithoutPreflightOrSkip();
        Assumptions.assumeTrue(settings.authMode() == DhanAuthMode.TOTP_GENERATED,
                "Production token refresh requires dhan.authMode=TOTP_GENERATED in config/dhan-local.properties");

        Path stateFile = settings.tokenStateFile();
        String previous = Files.exists(stateFile) ? Files.readString(stateFile) : "";
        Files.deleteIfExists(stateFile);

        String token = LiveDhanTestSupport.resolveLiveAccessToken(settings);
        assertTrue(Files.exists(stateFile), "Token state should be written to " + stateFile);
        assertTrue(new DhanAuthClient().fetchProfile(token, settings.refreshBufferMillis()).valid());
        if (!previous.isBlank()) {
            assertNotEquals(previous, Files.readString(stateFile), "Expected a freshly persisted token state");
        }
    }
}
