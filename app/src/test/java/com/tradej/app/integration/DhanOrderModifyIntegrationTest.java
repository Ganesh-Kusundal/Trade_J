package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
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
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-order")
class DhanOrderModifyIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void placesModifiesAndCancelsSandboxLimitOrder() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_ORDER_MODIFY_TEST_ENABLED", "dhan.orderModifyTestEnabled", "false")));
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_ORDER_TEST_ENABLED", "dhan.orderTestEnabled", "false")));

        String symbol = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SYMBOL", "dhan.testOrderSymbol", "TCS");
        String securityId = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SECURITY_ID", "dhan.testOrderSecurityId", "11536");
        String segmentCode = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SEGMENT", "dhan.testOrderSegment", "NSE_EQ");
        String exchangeCode = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_EXCHANGE", "dhan.testOrderExchange", "NSE");
        String quantity = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_QUANTITY", "dhan.testOrderQuantity", "1");

        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadInstrumentCatalog(writeCatalog(symbol, exchangeCode, segmentCode, securityId));

        long qty = Long.parseLong(quantity);
        Order placed = brokerConnection.orders().placeOrder(new OrderRequest(
                symbol,
                ExchangeSegment.valueOf(segmentCode.toUpperCase()),
                Side.BUY,
                qty,
                OrderType.LIMIT,
                1_00L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "omod-" + System.currentTimeMillis()
        ));
        assertFalse(placed.orderId().isBlank());
        LiveDhanTestSupport.trackSandboxOrderForCleanup(placed.orderId());

        long modifiedQty = qty + 1L;
        Order modified = brokerConnection.orders().modifyOrder(new ModifyOrderRequest(
                placed.orderId(),
                null,
                null,
                modifiedQty,
                2_00L,
                0L,
                OrderType.LIMIT,
                Validity.DAY
        ));
        assertEquals(placed.orderId(), modified.orderId());

        Order fetched = brokerConnection.orderQuery().getOrder(placed.orderId());
        assertEquals(placed.orderId(), fetched.orderId());
        assertEquals(modifiedQty, fetched.quantity());

        assertTrue(brokerConnection.orders().cancelOrder(placed.orderId()));
    }

    private Path writeCatalog(String symbol, String exchange, String segment, String securityId) throws Exception {
        Path file = Files.createTempFile("dhan-order-modify-catalog", ".csv");
        Files.writeString(file, """
                symbol,exchange,exchangeSegment,securityId
                %s,%s,%s,%s
                """.formatted(symbol, exchange, segment, securityId));
        return file;
    }
}
