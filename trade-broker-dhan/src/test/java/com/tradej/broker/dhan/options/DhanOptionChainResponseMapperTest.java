package com.tradej.broker.dhan.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.dhan.exceptions.DhanHttpException;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanOptionChainResponseMapperTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesExpiryListFromDataArray() throws Exception {
        DhanJsonResponse payload = json("""
                {
                  "data": ["2026-06-02", "2026-06-09", "2026-06-16"]
                }
                """);

        List<LocalDate> expiries = DhanOptionChainResponseMapper.parseExpiries(payload);

        assertEquals(List.of(
                LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 6, 9),
                LocalDate.of(2026, 6, 16)
        ), expiries);
    }

    @Test
    void parsesOptionChainStrikesAndSpot() throws Exception {
        DhanJsonResponse payload = json("""
                {
                  "data": {
                    "last_price": 23954.95,
                    "oc": {
                      "24000.000000": {
                        "ce": {"last_price": 148.2, "greeks": {"delta": 0.5}},
                        "pe": {"last_price": 120.0}
                      }
                    }
                  }
                }
                """);

        DhanOptionChainResponseMapper.OptionChainData chain = DhanOptionChainResponseMapper.parseChain(
                payload,
                "NIFTY",
                LocalDate.of(2026, 6, 2)
        );

        assertTrue(chain.spotPricePaisa() > 0L);
        assertEquals(1, chain.optionChain().size());
        assertTrue(chain.optionChain().path("24000.000000").path("ce").has("last_price"));
    }

    @Test
    void rejectsEmptyExpiryList() throws Exception {
        DhanJsonResponse payload = json("""
                {"data": []}
                """);

        assertThrows(DhanHttpException.class, () -> DhanOptionChainResponseMapper.parseExpiries(payload));
    }

    @Test
    void rejectsMissingOptionChainObject() throws Exception {
        DhanJsonResponse payload = json("""
                {"data": {"last_price": 100.0}}
                """);

        assertThrows(DhanHttpException.class, () -> DhanOptionChainResponseMapper.parseChain(
                payload,
                "NIFTY",
                LocalDate.of(2026, 6, 2)
        ));
    }

    private DhanJsonResponse json(String body) throws Exception {
        return new DhanJsonResponse(objectMapper.readTree(body));
    }
}
