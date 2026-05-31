package com.tradej.app.health;

import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.hotpath.MarketDataPipeline;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health indicator for the market data hot path.
 */
public class MarketDataHealthIndicator implements HealthIndicator {

    static final Duration DEFAULT_STALE_THRESHOLD = Duration.ofSeconds(30);

    private final MarketDataPipeline pipeline;
    private final Duration staleThreshold;
    private final BrokerTransportCapabilities transportCapabilities;

    public MarketDataHealthIndicator(
            MarketDataPipeline pipeline,
            ObjectProvider<BrokerTransportCapabilities> transportCapabilitiesProvider
    ) {
        this(pipeline, DEFAULT_STALE_THRESHOLD, transportCapabilitiesProvider.getIfAvailable());
    }

    MarketDataHealthIndicator(MarketDataPipeline pipeline, Duration staleThreshold) {
        this(pipeline, staleThreshold, null);
    }

    MarketDataHealthIndicator(
            MarketDataPipeline pipeline,
            Duration staleThreshold,
            BrokerTransportCapabilities transportCapabilities
    ) {
        this.pipeline = pipeline;
        this.staleThreshold = staleThreshold;
        this.transportCapabilities = transportCapabilities;
    }

    @Override
    public Health health() {
        if (transportCapabilities != null && !transportCapabilities.supportsWebSocket()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("status", "REST_ONLY");
            details.put("transport", "REST");
            details.put("websocketExpected", false);
            details.put("supportsRestMarketData", transportCapabilities.supportsRestMarketData());
            return Health.up().withDetails(details).build();
        }

        long lastTickMs = pipeline.lastTickTimestampMs();
        long totalTicks = pipeline.totalTicksProcessed();
        double tickRate = pipeline.tickRate();

        boolean receivedTicks = totalTicks > 0;
        boolean stale = receivedTicks
                && (System.currentTimeMillis() - lastTickMs) > staleThreshold.toMillis();

        String status;
        if (!receivedTicks) {
            status = "NO_TICKS_YET";
        } else if (stale) {
            status = "STALLED";
        } else {
            status = "ACTIVE";
        }

        boolean isUp = receivedTicks && !stale;

        Health.Builder builder = isUp ? Health.up() : Health.down();
        builder.withDetails(Map.of(
                "status", status,
                "totalTicks", totalTicks,
                "tickRate", Math.round(tickRate * 100.0) / 100.0,
                "lastTickTimestampMs", lastTickMs,
                "lastTickTimestampIso", lastTickMs > 0
                        ? Instant.ofEpochMilli(lastTickMs).toString()
                        : "never",
                "staleThresholdMs", staleThreshold.toMillis(),
                "websocketExpected", true
        ));
        return builder.build();
    }
}
