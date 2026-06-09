package com.tradej.broker.core.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class BrokerWebSocketMetricsTest {

    @Test
    void noop_doesNotThrow() {
        BrokerWebSocketMetrics noop = BrokerWebSocketMetrics.NOOP;
        assertDoesNotThrow(() -> {
            noop.recordReconnect();
            noop.recordSubscriptionCount(10);
            noop.recordMessageReceived();
            noop.recordParseError();
            noop.recordStaleDetected();
            noop.recordDroppedTick();
        });
    }

    @Test
    void micrometer_recordsReconnects() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerWebSocketMetrics metrics = new MicrometerBrokerWebSocketMetrics(registry, "dhan");

        metrics.recordReconnect();
        metrics.recordReconnect();
        metrics.recordReconnect();

        assertEquals(3.0, registry.counter("broker.ws.reconnects", "broker", "dhan").count());
    }

    @Test
    void micrometer_recordsMessages() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerWebSocketMetrics metrics = new MicrometerBrokerWebSocketMetrics(registry, "upstox");

        for (int i = 0; i < 100; i++) {
            metrics.recordMessageReceived();
        }

        assertEquals(100.0, registry.counter("broker.ws.messages", "broker", "upstox").count());
    }

    @Test
    void micrometer_recordsSubscriptionGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerWebSocketMetrics metrics = new MicrometerBrokerWebSocketMetrics(registry, "icici");

        metrics.recordSubscriptionCount(50);
        var gauge = registry.find("broker.ws.subscriptions").tag("broker", "icici").gauge();
        assertNotNull(gauge);
        assertEquals(50.0, gauge.value());

        metrics.recordSubscriptionCount(75);
        assertEquals(75.0, gauge.value());
    }

    @Test
    void micrometer_recordsParseErrors() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerWebSocketMetrics metrics = new MicrometerBrokerWebSocketMetrics(registry, "dhan");

        metrics.recordParseError();
        assertEquals(1.0, registry.counter("broker.ws.parse_errors", "broker", "dhan").count());
    }

    @Test
    void micrometer_recordsStaleAndDropped() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BrokerWebSocketMetrics metrics = new MicrometerBrokerWebSocketMetrics(registry, "dhan");

        metrics.recordStaleDetected();
        metrics.recordDroppedTick();
        metrics.recordDroppedTick();

        assertEquals(1.0, registry.counter("broker.ws.stale_detections", "broker", "dhan").count());
        assertEquals(2.0, registry.counter("broker.ws.dropped_ticks", "broker", "dhan").count());
    }
}
