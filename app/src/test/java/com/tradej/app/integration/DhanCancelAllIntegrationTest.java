package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-order")
class DhanCancelAllIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void cancelAllOpenOrdersClearsSandboxPlacement() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_CANCEL_ALL_TEST_ENABLED", "dhan.cancelAllTestEnabled", "false")));
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_ORDER_TEST_ENABLED", "dhan.orderTestEnabled", "false")));

        String symbol = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SYMBOL", "dhan.testOrderSymbol", "TCS");
        String securityId = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SECURITY_ID", "dhan.testOrderSecurityId", "11536");
        String segmentCode = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SEGMENT", "dhan.testOrderSegment", "NSE_EQ");
        String exchangeCode = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_EXCHANGE", "dhan.testOrderExchange", "NSE");
        String quantity = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_QUANTITY", "dhan.testOrderQuantity", "1");

        brokerConnection = DhanBrokerConnection.create(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadInstrumentCatalog(writeCatalog(symbol, exchangeCode, segmentCode, securityId));

        Order placed = brokerConnection.orders().placeOrder(new OrderRequest(
                symbol,
                ExchangeSegment.valueOf(segmentCode.toUpperCase()),
                Side.BUY,
                Long.parseLong(quantity),
                OrderType.LIMIT,
                1_00L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "ocancelall-" + System.currentTimeMillis()
        ));
        assertFalse(placed.orderId().isBlank());
        LiveDhanTestSupport.trackSandboxOrderForCleanup(placed.orderId());

        List<String> cancelled = brokerConnection.orders().cancelAllOpenOrders();
        assertNotNull(cancelled);

        boolean stillActive = brokerConnection.orderQuery().getOrderBook().stream()
                .filter(order -> placed.orderId().equals(order.orderId()))
                .anyMatch(order -> order.status().isActive());
        assertFalse(stillActive, "Placed order should not remain active after cancel-all.");

        List<String> secondPass = brokerConnection.orders().cancelAllOpenOrders();
        assertNotNull(secondPass);
    }

    private Path writeCatalog(String symbol, String exchange, String segment, String securityId) throws Exception {
        Path file = Files.createTempFile("dhan-cancel-all-catalog", ".csv");
        Files.writeString(file, """
                symbol,exchange,exchangeSegment,securityId
                %s,%s,%s,%s
                """.formatted(symbol, exchange, segment, securityId));
        return file;
    }
}
