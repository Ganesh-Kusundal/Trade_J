package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
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
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-order")
class DhanOrderQueryIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        LiveDhanTestSupport.cancelTrackedSandboxOrders(brokerConnection);
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void queriesSandboxOrderBookAfterPlacement() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(
                LiveDhanTestSupport.value("DHAN_ORDER_QUERY_TEST_ENABLED", "dhan.orderQueryTestEnabled", "false")));
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
                OrderType.MARKET,
                0L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "oquery-" + System.currentTimeMillis()
        ));
        assertFalse(placed.orderId().isBlank());
        LiveDhanTestSupport.trackSandboxOrderForCleanup(placed.orderId());

        Order fetched = brokerConnection.orderQuery().getOrder(placed.orderId());
        assertEquals(placed.orderId(), fetched.orderId());
        assertEquals(symbol, fetched.symbol());
        OrderStatus status = brokerConnection.orderQuery().getOrderStatus(placed.orderId());
        assertNotNull(status);
        assertFalse(status == OrderStatus.UNKNOWN, "Order status should be mapped from sandbox response.");

        List<Order> book = brokerConnection.orderQuery().getOrderBook();
        assertFalse(book.isEmpty());
        assertTrue(book.stream().anyMatch(order -> placed.orderId().equals(order.orderId())),
                "Order book should contain the placed sandbox order.");

        List<Trade> trades = brokerConnection.orderQuery().getTradeBook();
        assertNotNull(trades);
        List<Trade> orderTrades = trades.stream().filter(trade -> placed.orderId().equals(trade.orderId())).toList();
        OptionalLong executedPrice = brokerConnection.orderQuery().getExecutedPricePaisa(placed.orderId());
        OptionalLong exchangeTime = brokerConnection.orderQuery().getExchangeTimeMs(placed.orderId());
        if (!orderTrades.isEmpty()) {
            assertTrue(executedPrice.isPresent() && executedPrice.getAsLong() > 0L);
            assertTrue(exchangeTime.isPresent() && exchangeTime.getAsLong() > 0L);
        } else {
            assertTrue(executedPrice.isEmpty());
            assertTrue(exchangeTime.isEmpty());
        }

        assertTrue(brokerConnection.orders().cancelOrder(placed.orderId()));
    }

    private Path writeCatalog(String symbol, String exchange, String segment, String securityId) throws Exception {
        Path file = Files.createTempFile("dhan-order-query-catalog", ".csv");
        Files.writeString(file, """
                symbol,exchange,exchangeSegment,securityId
                %s,%s,%s,%s
                """.formatted(symbol, exchange, segment, securityId));
        return file;
    }
}
