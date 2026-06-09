package com.tradej.cli.output;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BrokerDebugRendererTest {

    @Test
    void render_basicRequest_containsAllSections() {
        BrokerDebugRenderer.DebugInfo info = BrokerDebugRenderer.DebugInfo.builder()
                .method("GET")
                .url("https://api.dhan.co/v2/market/quote")
                .statusCode(200)
                .latencyMs(14)
                .responseBody("{\"last_price\": 2543.50}")
                .mappedResult("Quote{ltpPaisa=254350}")
                .build();

        String result = BrokerDebugRenderer.render(info);
        assertTrue(result.contains("Request"));
        assertTrue(result.contains("Response"));
        assertTrue(result.contains("Mapping"));
        assertTrue(result.contains("GET"));
        assertTrue(result.contains("200"));
    }

    @Test
    void render_withHeaders_showsHeaders() {
        BrokerDebugRenderer.DebugInfo info = BrokerDebugRenderer.DebugInfo.builder()
                .method("POST")
                .url("https://api.example.com/v1/order")
                .requestHeaders(Map.of("Content-Type", "application/json", "access-token", "eyJhbGciOi"))
                .statusCode(201)
                .latencyMs(42)
                .build();

        String result = BrokerDebugRenderer.render(info);
        assertTrue(result.contains("Content-Type"));
        assertTrue(result.contains("application/json"));
    }

    @Test
    void render_sensitiveHeaders_areMasked() {
        BrokerDebugRenderer.DebugInfo info = BrokerDebugRenderer.DebugInfo.builder()
                .method("GET")
                .url("https://api.example.com")
                .requestHeaders(Map.of("access-token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
                .statusCode(200)
                .latencyMs(10)
                .build();

        String result = BrokerDebugRenderer.render(info);
        assertFalse(result.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"),
                "Full token should be masked");
        assertTrue(result.contains("eyJhbG...J9"),
                "Token should show first 6 and last 2 chars");
    }

    @Test
    void render_errorResponse_showsError() {
        BrokerDebugRenderer.DebugInfo info = BrokerDebugRenderer.DebugInfo.builder()
                .method("GET")
                .url("https://api.example.com/v1/quote")
                .statusCode(429)
                .latencyMs(5)
                .errorMessage("Rate limit exceeded")
                .build();

        String result = BrokerDebugRenderer.render(info);
        assertTrue(result.contains("429"));
        assertTrue(result.contains("Rate limit exceeded"));
    }

    @Test
    void render_withRateLimit_showsRemaining() {
        BrokerDebugRenderer.DebugInfo info = BrokerDebugRenderer.DebugInfo.builder()
                .method("GET")
                .url("https://api.example.com/v1/quote")
                .statusCode(200)
                .latencyMs(10)
                .rateLimitRemaining("95/100")
                .build();

        String result = BrokerDebugRenderer.render(info);
        assertTrue(result.contains("95/100"));
    }

    @Test
    void debugInfo_builder_works() {
        BrokerDebugRenderer.DebugInfo info = BrokerDebugRenderer.DebugInfo.builder()
                .method("GET")
                .url("https://example.com")
                .statusCode(200)
                .latencyMs(42)
                .build();

        assertEquals("GET", info.method());
        assertEquals("https://example.com", info.url());
        assertEquals(200, info.statusCode());
        assertEquals(42, info.latencyMs());
    }
}
