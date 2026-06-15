package com.tradej.app.integration;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.brokergateway.wiring.BrokerComposition;
import com.tradej.brokergateway.config.BrokerProfile;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live integration test for Upstox order lifecycle: place → query → cancel.
 * Requires sandbox credentials. Skips if not configured.
 */
@Tag("broker-order")
class UpstoxOrderLifecycleIntegrationTest {

    private IBrokerConnection broker;

    @AfterEach
    void tearDown() {
        if (broker != null) {
            try { broker.orders().cancelAllOpenOrders(); } catch (Exception ignored) {}
            broker.disconnect();
        }
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
    void placeAndCancelOrder() {
        IBrokerConnection conn = connect();

        OrderRequest request = new OrderRequest(
                "SBIN", ExchangeSegment.NSE_EQ, Side.BUY, 1,
                OrderType.LIMIT, 700_00L, 0L,
                ProductType.INTRADAY, Validity.DAY, "upstox-test-lifecycle"
        );

        Order placed = conn.orders().placeOrder(request);
        assertNotNull(placed, "Order should not be null");
        assertNotNull(placed.orderId(), "Order ID should be returned");

        List<Order> book = conn.orderQuery().getOrderBook();
        assertNotNull(book, "Order book should not be null");

        boolean cancelled = conn.orders().cancelOrder(placed.orderId());
        assertTrue(cancelled, "Cancel should succeed");
    }
}
