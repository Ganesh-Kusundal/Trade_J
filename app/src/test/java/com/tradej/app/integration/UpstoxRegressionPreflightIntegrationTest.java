package com.tradej.app.integration;

import com.tradej.broker.upstox.auth.UpstoxOAuthClient;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
@Tag("integration")
@Tag("upstox-preflight")
class UpstoxRegressionPreflightIntegrationTest {

    @Test
    void sandboxProfilePreflightSucceeds() {
        LiveUpstoxTestSupport.assumeIntegrationEnabled();
        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.sandboxConnectionSettingsOrSkip();
        UpstoxOAuthClient client = new UpstoxOAuthClient(
                HttpClient.newHttpClient(),
                "https://sandbox-api.upstox.com/v2"
        );
        long expiry = client.fetchProfile(settings.accessToken());
        assertTrue(expiry > System.currentTimeMillis(),
                "Upstox sandbox profile should return a future token expiry.");
    }

    @Test
    void sandboxCredentialsFileExistsWhenEnabled() {
        assumeTrue(LiveUpstoxTestSupport.integrationEnabled());
        assertTrue(
                java.nio.file.Files.exists(LiveUpstoxTestSupport.propertiesPath("config/upstox-sandbox.properties"))
                        || System.getenv("UPSTOX_SANDBOX_ACCESS_TOKEN") != null,
                "Expected sandbox credentials via file or env.");
    }

    @Test
    void analyticsTokenPreflightSucceeds() {
        assumeTrue(LiveUpstoxTestSupport.analyticsIntegrationEnabled());
        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.analyticsConnectionSettingsOrSkip();
        UpstoxOAuthClient client = new UpstoxOAuthClient(
                HttpClient.newHttpClient(),
                "https://api.upstox.com/v2"
        );
        assertTrue(client.validateReadOnlyToken(settings.analyticsToken()),
                "Upstox analytics token should pass read-only market status preflight on live API.");
    }
}
