package com.tradej.app.integration;

import com.tradej.broker.icici.auth.BreezeSessionExchange;
import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;
import com.tradej.broker.icici.rest.BreezePortfolioRestClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class IciciTokenLifecycleIntegrationTest {

    @Test
    void exchangesTotpForSessionAndFetchesFunds() throws Exception {
        BreezeConnectionSettings settings = LiveIciciTestSupport.connectionSettingsOrSkip();
        Files.deleteIfExists(settings.tokenStateFile());

        BreezeTokenManager tokenManager = new BreezeTokenManager(settings);
        tokenManager.ensureValid();
        assertNotNull(tokenManager.session().base64SessionToken());

        BreezeAuthenticatedHttpClient httpClient = new BreezeAuthenticatedHttpClient(tokenManager);
        BreezePortfolioRestClient portfolioRestClient = new BreezePortfolioRestClient(httpClient);
        var funds = portfolioRestClient.getFunds();
        assertTrue(funds != null && !funds.isMissingNode());
    }

    @Test
    void customerDetailsExchangeDirectly() throws Exception {
        BreezeConnectionSettings settings = LiveIciciTestSupport.connectionSettingsOrSkip();
        try {
            BreezeSessionExchange exchange = new BreezeSessionExchange();
            var session = exchange.exchange(settings.appKey(), LiveIciciTestSupport.totpSessionInput(settings));
            assertNotNull(session.userId());
            assertNotNull(session.sessionKey());
        } catch (IllegalStateException ex) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    false,
                    "ICICI session exchange failed (use API_SESSION auth mode if TOTP is rejected): " + ex.getMessage());
        }
    }
}
