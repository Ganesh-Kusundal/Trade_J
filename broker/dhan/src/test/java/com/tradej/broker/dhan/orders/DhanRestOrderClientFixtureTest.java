package com.tradej.broker.dhan.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.mapper.DhanSdkMapper;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("unit")
class DhanRestOrderClientFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesPlaceOrderFixtureIntoDomainOrder() throws Exception {
        DhanJsonResponse response = fixture("place-order-response.json");
        DhanInstrumentDefinition definition = new DhanInstrumentDefinition(
                "TCS",
                "TCS",
                Exchange.NSE,
                ExchangeSegment.NSE_EQ,
                "11536",
                "EQ",
                "TCS",
                null,
                null,
                null,
                1L,
                1L,
                "11536"
        );

        Order order = mapPlaceOrderResponse(response, definition);

        assertEquals("SANDBOX-ORDER-001", order.orderId());
        assertEquals("itest-redacted-001", order.correlationId());
        assertEquals("TCS", order.symbol());
        assertEquals(OrderStatus.PENDING, order.status());
        assertEquals(1L, order.quantity());
    }

    @Test
    void parsesCancelOrderFixtureStatus() throws Exception {
        DhanJsonResponse response = fixture("cancel-order-response.json");
        String status = response.string("orderStatus", "status");
        assertEquals("CANCELLED", status);
    }

    @Test
    void historicalFixtureExposesSeriesArrays() throws Exception {
        DhanJsonResponse response = fixture("historical-daily-response.json");
        assertFalse(response.path("open").isMissingNode());
        assertEquals(2, response.path("open").raw().size());
    }

    @Test
    void parsesOrdersListFixture() throws Exception {
        DhanJsonResponse response = fixture("orders-list-response.json");
        assertFalse(response.asList().isEmpty());
        DhanJsonResponse first = response.asList().getFirst();
        assertEquals("SANDBOX-ORDER-001", first.string("orderId", "id"));
    }

    @Test
    void parsesTradesListFixture() throws Exception {
        DhanJsonResponse response = fixture("trades-list-response.json");
        assertFalse(response.asList().isEmpty());
        DhanJsonResponse first = response.asList().getFirst();
        assertEquals("SANDBOX-ORDER-001", first.string("orderId"));
        assertEquals("SANDBOX-TRADE-001", first.string("exchangeTradeId", "tradeId", "id"));
        assertEquals(1L, first.longValue("tradedQuantity", "quantity"));
    }

    @Test
    void optionChainFixtureHasNestedData() throws Exception {
        DhanJsonResponse response = fixture("optionchain-response.json");
        assertEquals("NIFTY", response.path("data").string("underlyingSymbol"));
    }

    private Order mapPlaceOrderResponse(DhanJsonResponse response, DhanInstrumentDefinition definition) {
        DhanJsonResponse data = response.has("data") ? response.path("data") : response;
        String orderId = data.string("orderId", "id");
        return new Order(
                orderId,
                data.string("correlationId"),
                definition.canonicalSymbol(),
                definition.exchangeSegment(),
                DhanSdkMapper.side(data.string("transactionType")),
                DhanSdkMapper.productType(data.string("productType")),
                DhanSdkMapper.orderType(data.string("orderType")),
                DhanSdkMapper.orderStatus(data.string("orderStatus", "status")),
                data.longValue("quantity"),
                data.longValue("filledQty", "filledQuantity"),
                data.decimalPrice("price"),
                data.decimalPrice("triggerPrice"),
                0L,
                data.string("remarks", "message")
        );
    }

    private DhanJsonResponse fixture(String name) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/dhan-fixtures/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new DhanJsonResponse(mapper.readTree(json));
        }
    }
}
