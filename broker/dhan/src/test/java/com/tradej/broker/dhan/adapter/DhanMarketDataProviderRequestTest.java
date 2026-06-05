package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanMarketDataProviderRequestTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void marketFeedBodyUsesNumericSecurityIds() throws Exception {
        Method method = DhanMarketDataProvider.class.getDeclaredMethod(
                "buildMarketFeedRequestBody",
                Map.class
        );
        method.setAccessible(true);
        ObjectNode body = (ObjectNode) method.invoke(
                null,
                Map.of("NSE_EQ", List.of("3045", "11536"))
        );

        assertTrue(body.get("NSE_EQ").get(0).isNumber());
        assertTrue(body.get("NSE_EQ").get(1).isNumber());
        assertEquals("[3045,11536]", MAPPER.writeValueAsString(body.get("NSE_EQ")));
    }
}
