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
class DhanForeverOrderIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void placesForeverOrderWhenEnabled() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_FOREVER_ORDER_TEST_ENABLED", "dhan.foreverOrderTestEnabled", "false")));
        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-forever-cache"), false);
        OrderRequest request = new OrderRequest(
                LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SYMBOL", "dhan.testOrderSymbol", "TCS"),
                ExchangeSegment.valueOf(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SEGMENT", "dhan.testOrderSegment", "NSE_EQ")),
                Side.BUY,
                Long.parseLong(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_QUANTITY", "dhan.testOrderQuantity", "1")),
                OrderType.LIMIT,
                Long.parseLong(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_PRICE_PAISE", "dhan.testOrderPricePaise", "10000")),
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "forever-" + System.currentTimeMillis()
        );
        var order = LiveDhanTestSupport.assumeSandboxSupported(
                () -> brokerConnection.gttOrders().placeForeverOrder(request, "SINGLE", null, null, null),
                "forever-order");
        assertNotNull(order);
        LiveDhanTestSupport.trackSandboxOrderForCleanup(order.orderId());
    }
}
