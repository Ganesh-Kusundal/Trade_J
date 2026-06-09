package com.tradej.gateway.metrics;

import com.tradej.gateway.router.GatewayTopicRouter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Micrometer metrics for the WebSocket gateway.
 *
 * <p>Registers gauges for active connections, events sent/dropped, and topic subscriptions.
 */
@Component
public class GatewayMetrics {

    private final GatewayTopicRouter router;
    private final MeterRegistry meterRegistry;

    public GatewayMetrics(GatewayTopicRouter router, MeterRegistry meterRegistry) {
        this.router = router;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder("gateway.events.sent", router, r -> (double) r.sentEventCount())
                .description("Total gateway events successfully sent to clients")
                .register(meterRegistry);

        Gauge.builder("gateway.events.dropped", router, r -> (double) r.droppedEventCount())
                .description("Total gateway events dropped due to full queue")
                .register(meterRegistry);

        Gauge.builder("gateway.queue.depth", router, r -> (double) r.queueDepth())
                .description("Current depth of the gateway send queue")
                .register(meterRegistry);
    }
}
