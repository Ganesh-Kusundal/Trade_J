package com.tradej.broker.dhan.orders;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanRestOrderClientUnitTest {
    @Test
    void marketOrderPayloadIncludesClientIdAndOmitsPrice() throws Exception {
        DhanConnectionSettings settings = DhanConnectionSettings.sandboxWithDefaults("2505162156", "token");
        DhanRestOrderClient client = new DhanRestOrderClient(null, settings, new DhanApiUrlResolver(settings), null);
        OrderRequest request = new OrderRequest(
                "TCS",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                1L,
                OrderType.MARKET,
                0L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "itest123"
        );
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

        ObjectNode payload = invokeBaseOrderPayload(client, request, definition);

        assertEquals("2505162156", payload.get("dhanClientId").asText());
        assertEquals("11536", payload.get("securityId").asText());
        assertEquals("MARKET", payload.get("orderType").asText());
        assertEquals("itest123", payload.get("correlationId").asText());
        assertFalse(payload.has("price"));
    }

    @Test
    void limitOrderPayloadIncludesPrice() throws Exception {
        DhanConnectionSettings settings = DhanConnectionSettings.sandboxWithDefaults("2505162156", "token");
        DhanRestOrderClient client = new DhanRestOrderClient(null, settings, new DhanApiUrlResolver(settings), null);
        OrderRequest request = new OrderRequest(
                "TCS",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                1L,
                OrderType.LIMIT,
                3_500_00L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                null
        );
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

        ObjectNode payload = invokeBaseOrderPayload(client, request, definition);

        assertTrue(payload.has("price"));
        assertEquals(3500.0, payload.get("price").asDouble(), 0.01);
        assertFalse(payload.has("correlationId"));
    }

    private static ObjectNode invokeBaseOrderPayload(
            DhanRestOrderClient client,
            OrderRequest request,
            DhanInstrumentDefinition definition
    ) throws Exception {
        Method method = DhanRestOrderClient.class.getDeclaredMethod("baseOrderPayload", OrderRequest.class, DhanInstrumentDefinition.class);
        method.setAccessible(true);
        return (ObjectNode) method.invoke(client, request, definition);
    }
}
