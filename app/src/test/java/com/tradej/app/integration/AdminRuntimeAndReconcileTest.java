package com.tradej.app.integration;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link com.tradej.app.admin.AdminController} runtime,
 * reconcile, and rate-limit endpoints.
 */
class AdminRuntimeAndReconcileTest extends AdminTestBase {

    @SuppressWarnings("unchecked")
    @Test
    void runtimeReturnsState() {
        // Setup broker state
        when(brokerConnection.websocket()).thenReturn(webSocketMultiplexer);
        when(webSocketMultiplexer.isConnected()).thenReturn(true);
        when(webSocketMultiplexer.subscriptions()).thenReturn(Map.of(
                new MarketSubscriptionRequest("NIFTY", ExchangeSegment.IDX_I),
                FeedMode.QUOTE
        ));

        // Setup runtime health
        when(tradingCircuitBreaker.isOpen()).thenReturn(false);
        when(runtimeHealthState.catalogLoaded()).thenReturn(true);
        when(runtimeHealthState.catalogSize()).thenReturn(5000);
        when(runtimeHealthState.brokerPreflightPassed()).thenReturn(true);
        when(runtimeHealthState.startupCompleted()).thenReturn(true);

        ResponseEntity<Map> response = rest.getForEntity("/admin/runtime", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("websocketConnected")).isEqualTo(true);
        assertThat(body.get("circuitBreakerOpen")).isEqualTo(false);
        assertThat(body.get("subscriptions")).isEqualTo(1);
        assertThat(body.get("catalogLoaded")).isEqualTo(true);
        assertThat(body.get("catalogSize")).isEqualTo(5000);
        assertThat(body.get("brokerPreflightPassed")).isEqualTo(true);
        assertThat(body.get("startupCompleted")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void runtimeReturnsDefaultsWhenDisconnected() {
        when(brokerConnection.websocket()).thenReturn(webSocketMultiplexer);
        when(webSocketMultiplexer.isConnected()).thenReturn(false);
        when(webSocketMultiplexer.subscriptions()).thenReturn(Map.of());
        when(tradingCircuitBreaker.isOpen()).thenReturn(true);
        when(runtimeHealthState.catalogLoaded()).thenReturn(false);
        when(runtimeHealthState.catalogSize()).thenReturn(0);
        when(runtimeHealthState.brokerPreflightPassed()).thenReturn(false);
        when(runtimeHealthState.startupCompleted()).thenReturn(false);

        ResponseEntity<Map> response = rest.getForEntity("/admin/runtime", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("websocketConnected")).isEqualTo(false);
        assertThat(body.get("circuitBreakerOpen")).isEqualTo(true);
        assertThat(body.get("subscriptions")).isEqualTo(0);
        assertThat(body.get("catalogLoaded")).isEqualTo(false);
        assertThat(body.get("catalogSize")).isEqualTo(0);
        assertThat(body.get("brokerPreflightPassed")).isEqualTo(false);
        assertThat(body.get("startupCompleted")).isEqualTo(false);
    }

    @SuppressWarnings("unchecked")
    @Test
    void reconcileRejectsEmptyBody() {
        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/reconcile", null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("rejected");
        assertThat(body.get("reason")).isEqualTo("explicit expectedNetPositions payload is required");
    }

    @SuppressWarnings("unchecked")
    @Test
    void reconcileAcceptsValidPayload() {
        Map<String, Long> payload = Map.of("NSE_EQ::RELIANCE", 100L);

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/reconcile", payload, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("triggered");

        verify(orderReconciler).reconcile(eq(payload), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void rateLimitReturnsMetrics() {
        // Make a request first so there is at least one bucket entry
        rest.getForEntity("/admin/summary", Map.class);

        ResponseEntity<Map> response = rest.getForEntity("/admin/rate-limit", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("totalRequests");
        assertThat(body).containsKey("totalRejected");
        assertThat(body).containsKey("activeClients");
        assertThat(body).containsKey("endpoints");

        // totalRequests should be >= 1 (from the warm-up request)
        assertThat(((Number) body.get("totalRequests")).longValue()).isGreaterThanOrEqualTo(1);

        Map<String, Object> endpoints = (Map<String, Object>) body.get("endpoints");
        assertThat(endpoints).isNotEmpty();
        assertThat(endpoints).containsKey("/admin/summary");

        Map<String, Object> summaryMetrics = (Map<String, Object>) endpoints.get("/admin/summary");
        assertThat(summaryMetrics).containsKey("requests");
        assertThat(summaryMetrics).containsKey("rejected");
        assertThat(summaryMetrics).containsKey("capacity");
        assertThat(summaryMetrics).containsKey("tokensRemaining");
        assertThat((Integer) summaryMetrics.get("capacity")).isEqualTo(10); // ADMIN_CAPACITY
        assertThat(((Number) summaryMetrics.get("requests")).longValue()).isGreaterThanOrEqualTo(1);
    }
}
