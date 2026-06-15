package com.tradej.app.integration;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.brokergateway.wiring.BrokerComposition;
import com.tradej.brokergateway.config.BrokerProfile;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live integration test for ICICI option chain retrieval.
 * Requires ICICI credentials. Skips if not configured.
 */
@Tag("broker-rest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IciciOptionChainIntegrationTest {

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
    void getExpiries_returnsNonEmpty() {
        List<LocalDate> expiries = broker.options().getExpiries("NIFTY", ExchangeSegment.IDX_I);
        assertNotNull(expiries, "Expiries should not be null");
        assertFalse(expiries.isEmpty(), "Should return at least one expiry");
    }

    @Test
    void getOptionChain_returnsSnapshot() {
        List<LocalDate> expiries = broker.options().getExpiries("NIFTY", ExchangeSegment.IDX_I);
        if (expiries.isEmpty()) return;
        OptionChainSnapshot chain = broker.options().getOptionChain("NIFTY", ExchangeSegment.IDX_I, expiries.getFirst());
        assertNotNull(chain, "Option chain should not be null");
    }
}
