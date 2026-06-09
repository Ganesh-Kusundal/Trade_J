package com.tradej.app.integration;

import com.tradej.app.api.SymbolController;
import com.tradej.app.config.WebConfiguration.RateLimitFilter;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Minimal Spring Boot configuration for this test only.
 * <p>
 * {@link EnableAutoConfiguration} enables the embedded web server and all
 * auto-configuration (Jackson, MVC, etc.). Only the explicitly imported
 * {@link SymbolController} and {@link RateLimitFilter} are loaded — no broker
 * connections, persistence, or {@code AdminController}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({SymbolController.class, RateLimitFilter.class})
class SymbolControllerTestConfig {
}

/**
 * Integration test for {@link SymbolController} and {@link RateLimitFilter}.
 * <p>
 * Bootstraps a minimal embedded server with only the controller and filter —
 * no broker connections, persistence, or credentials required. The
 * {@link InstrumentResolver} is mocked to provide controlled test data.
 * <p>
 * Tests verify:
 * <ul>
 *   <li>Cache-Control headers on successful responses</li>
 *   <li>503 + Retry-After when the instrument catalog is not yet loaded</li>
 *   <li>429 + Retry-After + X-RateLimit headers when rate limited</li>
 *   <li>Token bucket refill over time</li>
 * </ul>
 */
@Tag("integration")
@Tag("api")
@SpringBootTest(
        classes = SymbolControllerTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.port=0"}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SymbolControllerIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @MockitoBean
    private InstrumentResolver instrumentResolver;

    /** The /api/v1/symbols endpoint burst capacity configured in {@link RateLimitFilter}. */
    private static final int SYMBOLS_BURST_CAPACITY = 20;

    @SuppressWarnings("unchecked")
    @Test
    void returnsSymbolsWithCacheControlHeaders() {
        when(instrumentResolver.isLoaded()).thenReturn(true);
        when(instrumentResolver.allInstruments()).thenReturn(List.of(
                new Instrument("RELIANCE", "RELIANCE", Exchange.NSE, ExchangeSegment.NSE_EQ,
                        "EQUITY", "RELIANCE", null, null, null, 1L, 5L),
                new Instrument("NIFTY", "NIFTY", Exchange.INDEX, ExchangeSegment.IDX_I,
                        "INDEX", "NIFTY", null, null, null, 1L, 5L)
        ));

        var response = rest.getForEntity("/api/v1/symbols", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl())
                .isEqualTo("max-age=60, must-revalidate, public");
        assertThat(response.getHeaders().getFirst("Cache-Ttl-Seconds")).isEqualTo("60");

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("count")).isEqualTo(2);
        assertThat(body.get("totalCount")).isEqualTo(2);
        assertThat(body.get("cacheTtlSeconds")).isEqualTo(60);

        List<Map<String, Object>> symbols = (List<Map<String, Object>>) body.get("symbols");
        assertThat(symbols).hasSize(2);
        assertThat(symbols.get(0).get("symbol")).isEqualTo("RELIANCE");
        assertThat(symbols.get(0).get("exchange")).isEqualTo("NSE");
        assertThat(symbols.get(0).get("exchangeSegment")).isEqualTo("NSE_EQ");
        assertThat(symbols.get(0).get("active")).isEqualTo(true);
        assertThat(symbols.get(1).get("symbol")).isEqualTo("NIFTY");
    }

    @SuppressWarnings("unchecked")
    @Test
    void returns503WhenCatalogNotLoaded() {
        when(instrumentResolver.isLoaded()).thenReturn(false);

        var response = rest.getForEntity("/api/v1/symbols", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("10");

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Instrument catalog not yet loaded");
        assertThat(body.get("retryAfterSeconds")).isEqualTo(10);
    }

    @SuppressWarnings("unchecked")
    @Test
    void rateLimitsAfterBurstCapacityExceeded() {
        when(instrumentResolver.isLoaded()).thenReturn(true);
        when(instrumentResolver.allInstruments()).thenReturn(List.of(
                new Instrument("RELIANCE", "RELIANCE", Exchange.NSE, ExchangeSegment.NSE_EQ,
                        "EQUITY", "RELIANCE", null, null, null, 1L, 5L)
        ));

        // Keep sending rapidly until the bucket is exhausted
        int requestCount = 0;
        ResponseEntity<Map> response;
        do {
            response = rest.getForEntity("/api/v1/symbols", Map.class);
            requestCount++;
        } while (response.getStatusCode().value() == 200
                && requestCount <= 100); // Safety limit

        // Verify we hit the rate limit
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(requestCount).as("Expected burst capacity around %d", SYMBOLS_BURST_CAPACITY)
                .isGreaterThanOrEqualTo(SYMBOLS_BURST_CAPACITY - 2); // Allow small refill margin

        // Verify rate limit headers
        var headers = response.getHeaders();
        assertThat(headers.getFirst("Retry-After")).isNotNull();
        assertThat(headers.getFirst("X-RateLimit-Limit")).isEqualTo(String.valueOf(SYMBOLS_BURST_CAPACITY));
        assertThat(headers.getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(headers.getFirst("X-RateLimit-Reset")).isNotNull();

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Too many requests");
        assertThat(body.get("retryAfterSeconds")).isNotNull();
        assertThat(body.get("path")).isEqualTo("/api/v1/symbols");
    }

    @SuppressWarnings("unchecked")
    @Test
    void allowsRequestAfterTokenRefill() throws InterruptedException {
        when(instrumentResolver.isLoaded()).thenReturn(true);
        when(instrumentResolver.allInstruments()).thenReturn(List.of(
                new Instrument("RELIANCE", "RELIANCE", Exchange.NSE, ExchangeSegment.NSE_EQ,
                        "EQUITY", "RELIANCE", null, null, null, 1L, 5L)
        ));

        // Exhaust the bucket by sending requests until rate-limited
        ResponseEntity<Map> blocked;
        int requestCount = 0;
        do {
            blocked = rest.getForEntity("/api/v1/symbols", Map.class);
            requestCount++;
        } while (blocked.getStatusCode().value() == 200
                && requestCount <= 100);

        assertThat(blocked.getStatusCode().value()).isEqualTo(429);

        // Wait for refill of at least 1 token (100ms at 10 tokens/sec, plus margin)
        Thread.sleep(200);

        // Should now be allowed through
        var allowed = rest.getForEntity("/api/v1/symbols", Map.class);
        assertThat(allowed.getStatusCode().value()).isEqualTo(200);
    }
}
