package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.Exchange;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-order")
class DhanOrderLifecycleIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void placesAndCancelsRealLimitOrder() {
        String enabled = LiveDhanTestSupport.value("DHAN_ORDER_TEST_ENABLED", "dhan.orderTestEnabled");
        String symbol = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SYMBOL", "dhan.testOrderSymbol", "TCS");
        String securityId = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SECURITY_ID", "dhan.testOrderSecurityId", "11536");
        String segmentCode = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_SEGMENT", "dhan.testOrderSegment", "NSE_EQ");
        String exchangeCode = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_EXCHANGE", "dhan.testOrderExchange", "NSE");
        String quantity = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_TEST_ORDER_QUANTITY", "dhan.testOrderQuantity", "1");

        Assumptions.assumeTrue("true".equalsIgnoreCase(enabled),
                "Set DHAN_ORDER_TEST_ENABLED=true to run the sandbox order lifecycle integration test.");
        Assumptions.assumeTrue(isPresent(symbol) && isPresent(securityId)
                        && isPresent(segmentCode) && isPresent(exchangeCode) && isPresent(quantity),
                "Provide DHAN_* order test environment variables before running this test.");

        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.sandboxConnectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );

        Exchange exchange = Exchange.valueOf(exchangeCode.toUpperCase());
        ExchangeSegment exchangeSegment = ExchangeSegment.valueOf(segmentCode.toUpperCase());
        brokerConnection.loadInstrumentCatalog(writeCatalog(symbol, exchange.name(), segmentCode, securityId));

        Order order = brokerConnection.orders().placeOrder(new OrderRequest(
                symbol,
                exchangeSegment,
                Side.BUY,
                Long.parseLong(quantity),
                OrderType.MARKET,
                0L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "itest-" + System.currentTimeMillis()
        ));

        assertFalse(order.orderId().isBlank(), "Expected Dhan to return a real order id.");
        LiveDhanTestSupport.trackSandboxOrderForCleanup(order.orderId());
        assertTrue(brokerConnection.orders().cancelOrder(order.orderId()), "Expected the sandbox Dhan order to be cancellable.");
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private Path writeCatalog(String symbol, String exchange, String segment, String securityId) {
        try {
            Path file = Files.createTempFile("dhan-order-catalog", ".csv");
            Files.writeString(file, """
                    symbol,exchange,exchangeSegment,securityId
                    %s,%s,%s,%s
                    """.formatted(symbol, exchange, segment, securityId));
            return file;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create temporary Dhan catalog", ex);
        }
    }
}
