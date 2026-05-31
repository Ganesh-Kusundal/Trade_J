package com.tradej.app.integration;

import com.tradej.broker.icici.auth.BreezeBrowserSessionCapture;
import com.tradej.broker.icici.auth.BreezeSession;
import com.tradej.broker.icici.auth.BreezeSessionExchange;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.config.IciciAuthMode;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;
import com.tradej.broker.icici.http.BreezeHttpException;
import com.tradej.broker.icici.rest.BreezePortfolioRestClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies signed ICICI REST calls use a checksum accepted by the live API.
 */
@Tag("integration")
@Tag("broker-rest")
class IciciAuthenticatedRequestIntegrationTest {

    @Test
    void signedFundsRequestDoesNotFailChecksumValidation() {
        BreezeConnectionSettings settings = LiveIciciTestSupport.connectionSettingsOrSkip();
        String apiSession = obtainApiSession(settings);
        Assumptions.assumeTrue(apiSession != null && !apiSession.isBlank(),
                "Could not obtain ICICI API session for checksum validation test");

        BreezeSession exchanged = new BreezeSessionExchange().exchange(settings.appKey(), apiSession);
        BreezeTokenProvider tokenProvider = fixedTokenProvider(settings, exchanged);
        BreezePortfolioRestClient portfolio = new BreezePortfolioRestClient(
                new BreezeAuthenticatedHttpClient(tokenProvider));

        try {
            var funds = portfolio.getFunds();
            assertNotNull(funds);
            assertTrue(!funds.isMissingNode(), "Funds response should contain Success payload");
        } catch (BreezeHttpException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Invalid Checksum")) {
                fail("ICICI rejected request checksum: " + ex.getMessage());
            }
            Assumptions.assumeTrue(
                    false,
                    "ICICI funds call failed after checksum passed: " + ex.getMessage());
        }
    }

    private static String obtainApiSession(BreezeConnectionSettings settings) {
        try {
            if (settings.authMode() == IciciAuthMode.BROWSER_AUTOMATED) {
                return new BreezeBrowserSessionCapture(settings).captureApiSession();
            }
            return LiveIciciTestSupport.totpSessionInput(settings);
        } catch (RuntimeException | java.io.IOException ex) {
            return null;
        }
    }

    private static BreezeTokenProvider fixedTokenProvider(BreezeConnectionSettings settings, BreezeSession session) {
        return new BreezeTokenProvider() {
            @Override
            public void ensureValid() {
            }

            @Override
            public BreezeSession session() {
                return session;
            }

            @Override
            public String appKey() {
                return settings.appKey();
            }

            @Override
            public String secretKey() {
                return settings.secretKey();
            }
        };
    }
}
