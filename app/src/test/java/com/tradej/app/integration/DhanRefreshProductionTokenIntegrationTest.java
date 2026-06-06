package com.tradej.app.integration;

import com.tradej.broker.dhan.auth.DhanAuthClient;
import com.tradej.broker.dhan.auth.DhanAuthRejectedException;
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
 *
 * <p>If the Dhan API rate limit (once every ~2 minutes) is active, the test skips gracefully
 * rather than failing, restoring the previous state file so the existing cached token is preserved.
 */
@Tag("integration")
@Tag("broker-auth-drill")
class DhanRefreshProductionTokenIntegrationTest {
    @Test
    void regeneratesProductionTokenStateViaTotp() throws Exception {
        DhanConnectionSettings settings = LiveDhanTestSupport.liveConnectionSettingsWithoutPreflightOrSkip();
        Assumptions.assumeTrue(settings.authMode() == DhanAuthMode.TOTP_GENERATED,
                "Production token refresh requires dhan.authMode=TOTP_GENERATED in config/dhan-local.properties");

        Path stateFile = settings.tokenStateFile();
        String previous = Files.exists(stateFile) ? Files.readString(stateFile) : "";
        Files.deleteIfExists(stateFile);

        try {
            String token = resolveWithRateLimitSkip(settings);
            assertTrue(Files.exists(stateFile), "Token state should be written to " + stateFile);
            assertTrue(new DhanAuthClient().fetchProfile(token, settings.refreshBufferMillis()).valid());
            if (!previous.isBlank()) {
                assertNotEquals(previous, Files.readString(stateFile), "Expected a freshly persisted token state");
            }
        } catch (org.opentest4j.TestAbortedException e) {
            // Restore previous state file if generation was skipped (rate limited)
            if (!previous.isBlank() && !Files.exists(stateFile)) {
                Files.writeString(stateFile, previous);
            }
            throw e;
        }
    }

    private static String resolveWithRateLimitSkip(DhanConnectionSettings settings) {
        try {
            return LiveDhanTestSupport.resolveLiveAccessToken(settings);
        } catch (DhanAuthRejectedException e) {
            if (e.rateLimited()) {
                Assumptions.abort("Dhan TOTP generation rate limited. Wait ~2 minutes between generations.");
            }
            throw e;
        }
    }
}
