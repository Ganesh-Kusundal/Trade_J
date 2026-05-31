package com.tradej.app.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link com.tradej.app.admin.AdminController} pipeline,
 * strategies, and summary endpoints.
 */
class AdminPipelineTest extends AdminTestBase {

    @SuppressWarnings("unchecked")
    @Test
    void pipelineReturnsAllMetrics() {
        // Disruptor metrics
        when(disruptorBusMetrics.shardCount()).thenReturn(2);
        when(disruptorBusMetrics.ringBufferRemainingCapacity()).thenReturn(4096L);
        when(disruptorBusMetrics.ringBufferSize()).thenReturn(8192);
        when(disruptorBusMetrics.dispatchQueueDepth()).thenReturn(0);
        when(disruptorBusMetrics.dispatchDroppedEventCount()).thenReturn(0L);
        when(disruptorBusMetrics.subscriberCount()).thenReturn(5);
        when(disruptorBusMetrics.isStarted()).thenReturn(true);

        // Execution handler
        when(executionHandler.queueDepth()).thenReturn(3);
        when(executionHandler.queueRemainingCapacity()).thenReturn(47);

        // Market data
        when(marketDataPipeline.totalTicksProcessed()).thenReturn(15_000L);
        when(marketDataPipeline.tickRate()).thenReturn(500.25);
        when(marketDataPipeline.lastTickTimestampMs()).thenReturn(1_700_000_000_000L);

        // Order pipeline
        when(orderPipeline.totalOrdersAccepted()).thenReturn(50L);
        when(orderPipeline.orderRate()).thenReturn(5.25);

        ResponseEntity<Map> response = rest.getForEntity("/admin/pipeline", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        // Disruptor
        assertThat(body.get("shardCount")).isEqualTo(2);
        assertThat(body.get("ringBufferRemainingCapacity")).isEqualTo(4096);
        assertThat(body.get("ringBufferSize")).isEqualTo(8192);
        assertThat(body.get("ringBufferUtilization")).isEqualTo(50.0);
        assertThat(body.get("dispatchQueueDepth")).isEqualTo(0);
        assertThat(body.get("dispatchDroppedEventCount")).isEqualTo(0);
        assertThat(body.get("subscriberCount")).isEqualTo(5);
        assertThat(body.get("started")).isEqualTo(true);

        // Execution
        assertThat(body.get("executionQueueDepth")).isEqualTo(3);
        assertThat(body.get("executionQueueRemainingCapacity")).isEqualTo(47);
        assertThat((Double) body.get("executionQueueUtilization")).isGreaterThan(0.0);

        // Market data
        assertThat(body.get("totalTicksProcessed")).isEqualTo(15000);
        assertThat(body.get("tickRate")).isEqualTo(500.25);
        assertThat(body.get("lastTickTimestampMs")).isEqualTo(1_700_000_000_000L);

        // Orders
        assertThat(body.get("totalOrdersAccepted")).isEqualTo(50);
        assertThat(body.get("orderRate")).isEqualTo(5.25);
        assertThat(body.get("signalRate")).isNull();
        assertThat(body.get("totalSignalsSubmitted")).isNull();
        assertThat(body.get("signalRateLimitedCount")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void strategiesReturnsPluginNames() {
        when(strategyEngine.pluginNames()).thenReturn(List.of("MACrossover", "RSIReversal"));

        ResponseEntity<Map> response = rest.getForEntity("/admin/strategies", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("pluginCount")).isEqualTo(2);

        List<String> plugins = (List<String>) body.get("plugins");
        assertThat(plugins).containsExactly("MACrossover", "RSIReversal");
    }

    @SuppressWarnings("unchecked")
    @Test
    void strategiesReturnsEmptyPluginsWhenNoneRegistered() {
        when(strategyEngine.pluginNames()).thenReturn(List.of());

        ResponseEntity<Map> response = rest.getForEntity("/admin/strategies", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("pluginCount")).isEqualTo(0);

        List<String> plugins = (List<String>) body.get("plugins");
        assertThat(plugins).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void summaryReturnsCombinedState() {
        // Runtime health
        when(runtimeHealthState.startupCompleted()).thenReturn(true);
        when(runtimeHealthState.brokerPreflightPassed()).thenReturn(true);
        when(runtimeHealthState.catalogLoaded()).thenReturn(true);
        when(runtimeHealthState.catalogSize()).thenReturn(5000);

        // Broker
        when(brokerConnection.websocket()).thenReturn(webSocketMultiplexer);
        when(webSocketMultiplexer.isConnected()).thenReturn(true);
        when(webSocketMultiplexer.subscriptions()).thenReturn(Map.of());
        when(tradingCircuitBreaker.isOpen()).thenReturn(false);

        // Disruptor
        when(disruptorBusMetrics.shardCount()).thenReturn(2);
        when(disruptorBusMetrics.ringBufferRemainingCapacity()).thenReturn(4096L);
        when(disruptorBusMetrics.ringBufferSize()).thenReturn(8192);
        when(disruptorBusMetrics.dispatchQueueDepth()).thenReturn(0);
        when(disruptorBusMetrics.dispatchDroppedEventCount()).thenReturn(0L);

        // Execution
        when(executionHandler.queueDepth()).thenReturn(1);

        // Market data
        when(marketDataPipeline.totalTicksProcessed()).thenReturn(50_000L);
        when(marketDataPipeline.tickRate()).thenReturn(750.0);

        // Orders
        when(orderPipeline.totalOrdersAccepted()).thenReturn(100L);
        when(orderPipeline.orderRate()).thenReturn(20.0);

        // Strategies
        when(strategyEngine.pluginNames()).thenReturn(List.of("MACrossover", "BollingerBand", "RSIReversal"));

        ResponseEntity<Map> response = rest.getForEntity("/admin/summary", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        // Runtime state
        assertThat(body.get("startupCompleted")).isEqualTo(true);
        assertThat(body.get("brokerPreflightPassed")).isEqualTo(true);
        assertThat(body.get("catalogLoaded")).isEqualTo(true);
        assertThat(body.get("catalogSize")).isEqualTo(5000);

        // Broker
        assertThat(body.get("websocketConnected")).isEqualTo(true);
        assertThat(body.get("subscriptions")).isEqualTo(0);
        assertThat(body.get("circuitBreakerOpen")).isEqualTo(false);

        // Pipeline
        assertThat(body.get("shardCount")).isEqualTo(2);
        assertThat(body.get("ringBufferUtilizationPct")).isEqualTo(50.0);
        assertThat(body.get("dispatchQueueDepth")).isEqualTo(0);
        assertThat(body.get("dispatchDroppedEvents")).isEqualTo(0);
        assertThat(body.get("executionQueueDepth")).isEqualTo(1);

        // Market data
        assertThat(body.get("totalTicks")).isEqualTo(50000);
        assertThat(body.get("tickRate")).isEqualTo(750.0);

        // Orders
        assertThat(body.get("totalOrders")).isEqualTo(100);
        assertThat(body.get("orderRate")).isEqualTo(20.0);
        assertThat(body.get("signalRate")).isNull();
        assertThat(body.get("totalSignals")).isNull();
        assertThat(body.get("signalRateLimited")).isNull();

        // Strategies
        List<String> strategyPlugins = (List<String>) body.get("strategyPlugins");
        assertThat(strategyPlugins).containsExactly("MACrossover", "BollingerBand", "RSIReversal");
    }

    @SuppressWarnings("unchecked")
    @Test
    void summaryReturnsDefaultsWhenNotStarted() {
        // Runtime health — all false/zero
        when(runtimeHealthState.startupCompleted()).thenReturn(false);
        when(runtimeHealthState.brokerPreflightPassed()).thenReturn(false);
        when(runtimeHealthState.catalogLoaded()).thenReturn(false);
        when(runtimeHealthState.catalogSize()).thenReturn(0);

        // Broker — disconnected, no subs, breaker open
        when(brokerConnection.websocket()).thenReturn(webSocketMultiplexer);
        when(webSocketMultiplexer.isConnected()).thenReturn(false);
        when(webSocketMultiplexer.subscriptions()).thenReturn(Map.of());
        when(tradingCircuitBreaker.isOpen()).thenReturn(true);

        // Disruptor — zero capacity, not started
        when(disruptorBusMetrics.shardCount()).thenReturn(1);
        when(disruptorBusMetrics.ringBufferRemainingCapacity()).thenReturn(1024L);
        when(disruptorBusMetrics.ringBufferSize()).thenReturn(1024);
        when(disruptorBusMetrics.dispatchQueueDepth()).thenReturn(0);
        when(disruptorBusMetrics.dispatchDroppedEventCount()).thenReturn(0L);

        // Execution
        when(executionHandler.queueDepth()).thenReturn(0);

        // Market data — no ticks yet
        when(marketDataPipeline.totalTicksProcessed()).thenReturn(0L);
        when(marketDataPipeline.tickRate()).thenReturn(0.0);

        // Orders — none
        when(orderPipeline.totalOrdersAccepted()).thenReturn(0L);
        when(orderPipeline.orderRate()).thenReturn(0.0);

        // Strategies — empty
        when(strategyEngine.pluginNames()).thenReturn(List.of());

        ResponseEntity<Map> response = rest.getForEntity("/admin/summary", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        // Runtime state
        assertThat(body.get("startupCompleted")).isEqualTo(false);
        assertThat(body.get("brokerPreflightPassed")).isEqualTo(false);
        assertThat(body.get("catalogLoaded")).isEqualTo(false);
        assertThat(body.get("catalogSize")).isEqualTo(0);

        // Broker
        assertThat(body.get("websocketConnected")).isEqualTo(false);
        assertThat(body.get("subscriptions")).isEqualTo(0);
        assertThat(body.get("circuitBreakerOpen")).isEqualTo(true);

        // Pipeline — all zeros
        assertThat(body.get("ringBufferUtilizationPct")).isEqualTo(0.0);

        // Market data
        assertThat(body.get("totalTicks")).isEqualTo(0);
        assertThat(body.get("tickRate")).isEqualTo(0.0);

        // Orders
        assertThat(body.get("totalOrders")).isEqualTo(0);
        assertThat(body.get("orderRate")).isEqualTo(0.0);
        assertThat(body.get("signalRate")).isNull();
        assertThat(body.get("totalSignals")).isNull();
        assertThat(body.get("signalRateLimited")).isNull();

        // Strategies
        List<String> strategyPlugins = (List<String>) body.get("strategyPlugins");
        assertThat(strategyPlugins).isEmpty();
    }
}
