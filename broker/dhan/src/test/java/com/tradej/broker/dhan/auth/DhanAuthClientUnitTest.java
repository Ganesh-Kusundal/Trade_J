package com.tradej.broker.dhan.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanAuthClientUnitTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesGenerateTokenExpiryTimeIntoTokenState() throws Exception {
        FakeAuthClient client = new FakeAuthClient("""
                {
                  "accessToken": "abc123",
                  "expiryTime": "2026-05-27T08:30:00.000"
                }
                """);

        DhanTokenState state = client.generateViaTotp("1106251237", "960000", "123456");

        long expected = ZonedDateTime.of(2026, 5, 27, 8, 30, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        assertEquals("abc123", state.accessToken());
        assertEquals(expected, state.expiryEpochMs());
        assertEquals("TOTP_GENERATED", state.source());
    }

    @Test
    void parsesSnakeCaseTokenFieldsFromNestedData() throws Exception {
        FakeAuthClient client = new FakeAuthClient("""
                {
                  "data": {
                    "access_token": "nested-token",
                    "expiry_time": "2026-05-27T08:30:00.000"
                  }
                }
                """);

        DhanTokenState state = client.generateViaTotp("1106251237", "960000", "123456");

        assertEquals("nested-token", state.accessToken());
        assertEquals("TOTP_GENERATED", state.source());
    }

    @Test
    void rejectsRateLimitedTokenGenerationWithTypedException() {
        FakeAuthClient client = new FakeAuthClient("""
                {
                  "status": "error",
                  "message": "Token can be generated once every 2 minutes."
                }
                """);

        DhanAuthRejectedException ex = assertThrows(
                DhanAuthRejectedException.class,
                () -> client.generateViaTotp("1106251237", "960000", "123456"));

        assertTrue(ex.rateLimited());
        assertTrue(ex.getMessage().contains("2 minutes"));
    }

    @Test
    void parsesProfileTokenValidityIntoTokenInfo() throws Exception {
        // Use a far-future expiry so the time-based validity check passes
        FakeAuthClient client = new FakeAuthClient("""
                {
                  "dhanClientId": "1106251237",
                  "tokenValidity": "27/05/2099 08:30"
                }
                """);

        DhanTokenInfo info = client.fetchProfile("abc123", 10 * 60_000L);

        long expected = ZonedDateTime.of(2099, 5, 27, 8, 30, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        assertTrue(info.valid());
        assertEquals(expected, info.expiryEpochMs());
    }

    private final class FakeAuthClient extends DhanAuthClient {
        private final String payload;

        private FakeAuthClient(String payload) {
            this.payload = payload;
        }

        @Override
        protected com.fasterxml.jackson.databind.JsonNode send(HttpRequest request, String action) {
            try {
                com.fasterxml.jackson.databind.JsonNode body = objectMapper.readTree(payload);
                verifyBusinessSuccess(body, action);
                return body;
            } catch (DhanAuthRejectedException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
