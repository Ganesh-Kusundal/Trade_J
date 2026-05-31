package com.tradej.app.integration;

import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.config.IciciAuthMode;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;
import com.tradej.broker.icici.rest.BreezePortfolioRestClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exchanges API_Session or TOTP for a signed Breeze session and persists
 * {@code runtime/icici-token-state.json}. Run via {@code ./scripts/refresh-icici-session.sh}.
 */
@Tag("integration")
@Tag("broker-auth-drill")
class IciciRefreshSessionIntegrationTest {

    @Test
    void regeneratesProductionSessionState() throws Exception {
        Assumptions.assumeTrue(isDrillEnabled(),
                "Set ICICI_SESSION_DRILL=true to run destructive session refresh drill.");

        BreezeConnectionSettings settings = LiveIciciTestSupport.connectionSettingsOrSkip();
        Path stateFile = settings.tokenStateFile();
        String previous = Files.exists(stateFile) ? Files.readString(stateFile) : "";
        Files.deleteIfExists(stateFile);

        BreezeTokenManager tokenManager = new BreezeTokenManager(settings);
        tokenManager.ensureValid();

        var session = tokenManager.session();
        assertNotNull(session.base64SessionToken());
        assertFalse(session.base64SessionToken().isBlank());
        assertTrue(Files.exists(stateFile), "Token state should be written to " + stateFile);

        BreezeAuthenticatedHttpClient httpClient = new BreezeAuthenticatedHttpClient(tokenManager);
        var funds = new BreezePortfolioRestClient(httpClient).getFunds();
        assertTrue(funds != null && !funds.isMissingNode(),
                "Funds call failed after session exchange — session may be invalid");

        if (!previous.isBlank()) {
            assertFalse(previous.equals(Files.readString(stateFile)),
                    "Expected freshly persisted token state");
        }

        System.out.println("ICICI session refreshed via " + settings.authMode()
                + " for user " + session.userId()
                + "; state file: " + stateFile.toAbsolutePath());
    }

    private static boolean isDrillEnabled() {
        return "true".equalsIgnoreCase(System.getenv("ICICI_SESSION_DRILL"))
                || "true".equalsIgnoreCase(System.getProperty("ICICI_SESSION_DRILL"));
    }
}
