package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
@Tag("broker-rest")
class DhanOrderQueryLiveIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void readsLiveOrderAndTradeBooksViaSdk() {
        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );

        List<Order> orders = brokerConnection.orderQuery().getOrderBook();
        List<Trade> trades = brokerConnection.orderQuery().getTradeBook();
        assertNotNull(orders);
        assertNotNull(trades);

        if (!orders.isEmpty()) {
            Order first = orders.getFirst();
            assertFalse(first.orderId().isBlank(), "Live order book entries should have order ids.");
            assertNotNull(first.exchangeSegment(), "Live order book entries should expose exchange segment.");
        }
        if (!trades.isEmpty()) {
            Trade first = trades.getFirst();
            assertFalse(first.tradeId().isBlank(), "Live trade book entries should have trade ids.");
            assertNotNull(first.exchangeSegment(), "Live trade book entries should expose exchange segment.");
        }
    }
}
