package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanPortfolioIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void readsLivePortfolioEndpoints() throws Exception {
        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-portfolio-cache"), false);

        String expectedClientId = LiveDhanTestSupport.value("DHAN_CLIENT_ID", "dhan.clientId");
        Balance balance = brokerConnection.portfolio().getBalance();
        assertNotNull(balance.clientId());
        assertEquals(expectedClientId, balance.clientId());
        assertTrue(balance.cashPaisa() >= 0L);

        List<Position> positions = brokerConnection.portfolio().getPositions();
        assertNotNull(positions);
        for (Position position : positions) {
            assertFalse(position.symbol().isBlank(), "Position symbol must be set.");
            assertNotNull(position.exchangeSegment(), "Position exchange segment must be set.");
        }

        List<Holding> holdings = brokerConnection.portfolio().getHoldings();
        assertNotNull(holdings);
        for (Holding holding : holdings) {
            assertFalse(holding.symbol().isBlank(), "Holding symbol must be set.");
            assertTrue(holding.totalQuantity() >= 0L, "Holding quantity must be non-negative.");
        }
    }
}
