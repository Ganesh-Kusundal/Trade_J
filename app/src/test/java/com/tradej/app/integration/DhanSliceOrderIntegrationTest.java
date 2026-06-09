package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.SliceOrderRequest;
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

import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("integration")
@Tag("broker-order")
class DhanSliceOrderIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void placesSliceOrderWhenEnabled() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_SLICE_ORDER_TEST_ENABLED", "dhan.sliceOrderTestEnabled", "false")));
        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("dhan-slice-cache"), false);
        var orders = LiveDhanTestSupport.assumeSandboxSupported(() -> brokerConnection.sliceOrders().placeSliceOrder(new SliceOrderRequest(
                LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SYMBOL", "dhan.testOrderSymbol", "TCS"),
                ExchangeSegment.valueOf(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SEGMENT", "dhan.testOrderSegment", "NSE_EQ")),
                Side.BUY,
                Long.parseLong(LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_QUANTITY", "dhan.testOrderQuantity", "1")),
                OrderType.MARKET,
                0L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                false,
                "slice-" + System.currentTimeMillis()
        )), "slice-order");
        assertFalse(orders.isEmpty());
        orders.forEach(order -> LiveDhanTestSupport.trackSandboxOrderForCleanup(order.orderId()));
    }
}
