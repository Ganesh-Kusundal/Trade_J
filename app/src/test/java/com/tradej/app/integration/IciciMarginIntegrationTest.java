package com.tradej.app.integration;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live integration test for ICICI margin estimation.
 * Requires ICICI credentials. Skips if not configured.
 */
@Tag("broker-rest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IciciMarginIntegrationTest {

    private IBrokerConnection broker;

    @BeforeAll
    void setUp() throws Exception {
        BreezeConnectionSettings settings = LiveIciciTestSupport.connectionSettingsOrSkip();
        LiveIciciTestSupport.preflightSessionOrSkip(settings);
        BrokerProfile profile = new BrokerProfile(BrokerProfile.BrokerType.ICICI, null, null,
                new BrokerProfile.IciciConfig(
                        settings.appKey(), settings.secretKey(), settings.staticSessionToken(),
                        settings.authMode(), settings.totpSecretFile(),
                        settings.usernameFile(), settings.passwordFile(),
                        settings.apiSessionFile(), settings.tokenStateFile(),
                        settings.ordersEnabled(), settings.refreshBufferMinutes(),
                        settings.loginRedirectPort(), settings.loginRedirectPath(),
                        settings.browserHeadless(), settings.browserLoginTimeoutSeconds()));
        BrokerComposition comp = BrokerComposition.create(profile);
        broker = comp.brokerConnection();
    }

    @AfterAll
    void tearDown() {
        if (broker != null) broker.disconnect();
    }

    @Test
    void estimateMargin_returnsNonNull() {
        MarginEstimateRequest request = new MarginEstimateRequest(
                "RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY, 1L,
                ProductType.INTRADAY, OrderType.LIMIT, 2500_00L, 0L
        );
        MarginEstimate estimate = broker.margin().estimateMargin(request);
        assertNotNull(estimate, "Margin estimate should not be null");
        assertTrue(estimate.totalMarginPaisa() >= 0, "Total margin should be non-negative");
    }
}
