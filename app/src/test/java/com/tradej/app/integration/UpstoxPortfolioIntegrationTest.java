package com.tradej.app.integration;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live integration test for Upstox portfolio operations: balance, positions, holdings.
 * Requires sandbox credentials. Skips if not configured.
 */
@Tag("broker-rest")
class UpstoxPortfolioIntegrationTest {

    private IBrokerConnection broker;

    @AfterEach
    void tearDown() {
        if (broker != null) broker.disconnect();
    }

    private IBrokerConnection connect() {
        UpstoxConnectionSettings settings = LiveUpstoxTestSupport.sandboxConnectionSettingsOrSkip();
        BrokerProfile profile = new BrokerProfile(BrokerProfile.BrokerType.UPSTOX, null,
                new BrokerProfile.UpstoxConfig(
                        settings.clientId(), settings.clientSecret(), settings.redirectUri(),
                        settings.accessToken(), settings.refreshToken(),
                        settings.analyticsToken(), settings.extendedToken(),
                        settings.analyticsOnly(), settings.isSandbox(),
                        settings.redirectServerPort(), settings.refreshBufferMs(),
                        settings.tokenExpiryBufferMs()),
                null);
        BrokerComposition comp = BrokerComposition.create(profile);
        broker = comp.brokerConnection();
        return broker;
    }

    @Test
    void fetchesBalance() {
        IBrokerConnection conn = connect();
        Balance balance = conn.portfolio().getBalance();
        assertNotNull(balance, "Balance should not be null");
    }

    @Test
    void fetchesPositions() {
        IBrokerConnection conn = connect();
        List<Position> positions = conn.portfolio().getPositions();
        assertNotNull(positions, "Positions should not be null");
    }

    @Test
    void fetchesHoldings() {
        IBrokerConnection conn = connect();
        List<Holding> holdings = conn.portfolio().getHoldings();
        assertNotNull(holdings, "Holdings should not be null");
    }
}
