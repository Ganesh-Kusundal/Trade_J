package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
@Tag("broker-order")
class DhanSuperOrderIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void placesSuperOrderWhenEnabled() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_SUPER_ORDER_TEST_ENABLED", "dhan.superOrderTestEnabled", "false")));
        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-super-cache"), false);
        OrderRequest request = new OrderRequest(
                LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SYMBOL", "dhan.testOrderSymbol", "TCS"),
                ExchangeSegment.valueOf(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SEGMENT", "dhan.testOrderSegment", "NSE_EQ")),
                Side.BUY,
                Long.parseLong(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_QUANTITY", "dhan.testOrderQuantity", "1")),
                OrderType.MARKET,
                0L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "super-" + System.currentTimeMillis()
        );
        var order = LiveDhanTestSupport.assumeSandboxSupported(
                () -> brokerConnection.bracketOrders().placeSuperOrder(request, 1000L, 1000L, 0L),
                "super-order");
        assertNotNull(order);
        LiveDhanTestSupport.trackSandboxOrderForCleanup(order.orderId());
    }
}
