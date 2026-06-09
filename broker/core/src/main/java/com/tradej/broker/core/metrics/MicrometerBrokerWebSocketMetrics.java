package com.tradej.broker.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer-backed implementation of {@link BrokerWebSocketMetrics}.
 */
public final class MicrometerBrokerWebSocketMetrics implements BrokerWebSocketMetrics {

    private final Counter reconnectCounter;
    private final Counter messageCounter;
    private final Counter parseErrorCounter;
    private final Counter staleCounter;
    private final Counter droppedTickCounter;
    private final AtomicInteger subscriptionGauge;

    public MicrometerBrokerWebSocketMetrics(MeterRegistry registry, String broker) {
        this.reconnectCounter = Counter.builder("broker.ws.reconnects")
                .tag("broker", broker)
                .description("WebSocket reconnect attempts")
                .register(registry);
        this.messageCounter = Counter.builder("broker.ws.messages")
                .tag("broker", broker)
                .description("WebSocket messages received")
                .register(registry);
        this.parseErrorCounter = Counter.builder("broker.ws.parse_errors")
                .tag("broker", broker)
                .description("WebSocket message parse errors")
                .register(registry);
        this.staleCounter = Counter.builder("broker.ws.stale_detections")
                .tag("broker", broker)
                .description("Stale feed detections")
                .register(registry);
        this.droppedTickCounter = Counter.builder("broker.ws.dropped_ticks")
                .tag("broker", broker)
                .description("Ticks dropped by dedup filter")
                .register(registry);
        this.subscriptionGauge = new AtomicInteger(0);
        registry.gauge("broker.ws.subscriptions",
                io.micrometer.core.instrument.Tags.of("broker", broker),
                subscriptionGauge);
    }

    @Override
    public void recordReconnect() {
        reconnectCounter.increment();
    }

    @Override
    public void recordSubscriptionCount(int count) {
        subscriptionGauge.set(count);
    }

    @Override
    public void recordMessageReceived() {
        messageCounter.increment();
    }

    @Override
    public void recordParseError() {
        parseErrorCounter.increment();
    }

    @Override
    public void recordStaleDetected() {
        staleCounter.increment();
    }

    @Override
    public void recordDroppedTick() {
        droppedTickCounter.increment();
    }
}
