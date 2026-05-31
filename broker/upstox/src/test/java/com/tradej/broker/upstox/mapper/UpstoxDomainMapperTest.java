package com.tradej.broker.upstox.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxDomainMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private UpstoxDomainMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UpstoxDomainMapper();
    }

    @Test
    void mapsPlaceOrderResponseToOrder() throws Exception {
        JsonNode response = MAPPER.readTree(getClass().getResourceAsStream(
                "/upstox-fixtures/place-order-response.json"));

        var order = mapper.toOrder(response, null);
        assertNotNull(order);
        assertEquals("TEST_ORD_001", order.orderId());
        assertEquals("SBIN", order.symbol());
        assertEquals(10L, order.quantity());
        assertEquals(85050L, order.pricePaisa());
    }

    @Test
    void mapsCancelOrderResponse() throws Exception {
        JsonNode response = MAPPER.readTree(getClass().getResourceAsStream(
                "/upstox-fixtures/cancel-order-response.json"));

        assertTrue(mapper.isSuccess(response));
    }

    @Test
    void mapsMarketQuoteResponse() throws Exception {
        JsonNode response = MAPPER.readTree(getClass().getResourceAsStream(
                "/upstox-fixtures/market-quote-response.json"));

        // Verify the structure parses correctly
        JsonNode data = response.get("data");
        assertNotNull(data);
        assertTrue(data.has("SBIN"));
        assertEquals(850.50, data.get("SBIN").get("ltp").asDouble());
    }

    @Test
    void productTypeMapping() {
        assertEquals("MIS", UpstoxDomainMapper.mapProductType(com.tradej.core.domain.value.ProductType.INTRADAY));
        assertEquals("CNC", UpstoxDomainMapper.mapProductType(com.tradej.core.domain.value.ProductType.CNC));
        assertEquals("NRML", UpstoxDomainMapper.mapProductType(com.tradej.core.domain.value.ProductType.CARRY_FORWARD));
    }
}
