package com.tradej.gateway.health;

import com.tradej.gateway.router.GatewayTopicRouter;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health indicator for the WebSocket gateway.
 *
 * <p>Reports {@code UP} when the {@link GatewayTopicRouter} is running and accepting connections.
 * Includes details on active connections, events sent/dropped, and queue depth.
 */
@Component
public class GatewayHealthIndicator implements HealthIndicator {

    private final GatewayTopicRouter router;

    public GatewayHealthIndicator(GatewayTopicRouter router) {
        this.router = router;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        long sent = router.sentEventCount();
        long dropped = router.droppedEventCount();
        int queueDepth = router.queueDepth();

        details.put("eventsSent", sent);
        details.put("eventsDropped", dropped);
        details.put("queueDepth", queueDepth);

        return Health.up()
                .withDetails(details)
                .build();
    }
}
