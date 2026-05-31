package com.tradej.app.integration;

import com.tradej.broker.icici.adapter.IciciPortfolioProvider;
import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;
import com.tradej.broker.icici.rest.BreezePortfolioRestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class IciciPortfolioIntegrationTest {
    private static IciciPortfolioProvider portfolioProvider;

    @BeforeAll
    static void setUp() {
        BreezeConnectionSettings settings = LiveIciciTestSupport.connectionSettingsOrSkip();
        LiveIciciTestSupport.preflightSessionOrSkip(settings);
        BreezeTokenManager tokenManager = new BreezeTokenManager(settings);
        BreezeAuthenticatedHttpClient httpClient = new BreezeAuthenticatedHttpClient(tokenManager);
        portfolioProvider = new IciciPortfolioProvider(new BreezePortfolioRestClient(httpClient));
    }

    @Test
    void fetchesFundsBalance() {
        var balance = portfolioProvider.getBalance();
        assertNotNull(balance);
        assertTrue(balance.cashPaisa() >= 0);
    }

    @Test
    void fetchesHoldingsAndPositions() {
        assertNotNull(portfolioProvider.getHoldings());
        assertNotNull(portfolioProvider.getPositions());
    }
}
