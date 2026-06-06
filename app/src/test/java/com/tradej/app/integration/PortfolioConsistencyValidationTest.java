package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Trade;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("live")
@Isolated
class PortfolioConsistencyValidationTest {

    private DhanBrokerConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DhanBrokerConnection.create(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                new CaffeineIdempotencyCache());
        connection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-portfolio-val"), false);
    }

    @AfterEach
    void tearDown() {
        if (connection != null) connection.disconnect();
    }

    @Test
    void balanceIsNotNull() {
        Balance balance = connection.portfolio().getBalance();
        assertNotNull(balance, "Balance must not be null");
    }

    @Test
    void cashIsConsistent() {
        Balance balance = connection.portfolio().getBalance();
        long total = balance.withdrawablePaisa() + balance.utilizedPaisa();
        long tolerance = Math.max(Math.abs(balance.cashPaisa()) / 100, 100);
        assertTrue(Math.abs(balance.cashPaisa() - total) <= tolerance,
                "Cash ~ withdrawable + utilized: cash=" + balance.cashPaisa()
                        + " withdrawable=" + balance.withdrawablePaisa()
                        + " utilized=" + balance.utilizedPaisa());
    }

    @Test
    void positionsAreNotNull() {
        List<Position> positions = connection.portfolio().getPositions();
        assertNotNull(positions, "Positions must not be null");
    }

    @Test
    void holdingsAreNotNull() {
        List<Holding> holdings = connection.portfolio().getHoldings();
        assertNotNull(holdings, "Holdings must not be null");
    }

    @Test
    void orderBookIsNotNull() {
        List<Order> orders = connection.orderQuery().getOrderBook();
        assertNotNull(orders, "Order book must not be null");
    }

    @Test
    void tradeBookIsNotNull() {
        List<Trade> trades = connection.orderQuery().getTradeBook();
        assertNotNull(trades, "Trade book must not be null");
    }

    @Test
    void holdingQuantitiesAreNonNegative() {
        List<Holding> holdings = connection.portfolio().getHoldings();
        for (Holding h : holdings) {
            assertTrue(h.totalQuantity() >= 0,
                    "Holding quantity >= 0 for " + h.symbol() + ", got: " + h.totalQuantity());
        }
    }
}
