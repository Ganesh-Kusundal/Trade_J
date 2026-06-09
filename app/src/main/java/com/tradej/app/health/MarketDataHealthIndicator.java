package com.tradej.app.health;

import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.hotpath.MarketDataPipeline;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Clock;
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
    private final Clock clock;
    private final AlertManager alertManager;

    public MarketDataHealthIndicator(
            MarketDataPipeline pipeline,
            ObjectProvider<BrokerTransportCapabilities> transportCapabilitiesProvider,
            AlertManager alertManager
    ) {
        this(pipeline, DEFAULT_STALE_THRESHOLD, transportCapabilitiesProvider.getIfUnique(), Clock.systemUTC(), alertManager);
    }

    MarketDataHealthIndicator(MarketDataPipeline pipeline, Duration staleThreshold) {
        this(pipeline, staleThreshold, null, Clock.systemUTC(), null);
    }

    MarketDataHealthIndicator(
            MarketDataPipeline pipeline,
            Duration staleThreshold,
            BrokerTransportCapabilities transportCapabilities
    ) {
        this(pipeline, staleThreshold, transportCapabilities, Clock.systemUTC(), null);
    }

    MarketDataHealthIndicator(
            MarketDataPipeline pipeline,
            Duration staleThreshold,
            BrokerTransportCapabilities transportCapabilities,
            Clock clock
    ) {
        this(pipeline, staleThreshold, transportCapabilities, clock, null);
    }

    MarketDataHealthIndicator(
            MarketDataPipeline pipeline,
            Duration staleThreshold,
            BrokerTransportCapabilities transportCapabilities,
            Clock clock,
            AlertManager alertManager
    ) {
        this.pipeline = pipeline;
        this.staleThreshold = staleThreshold;
        this.transportCapabilities = transportCapabilities;
        this.clock = clock;
        this.alertManager = alertManager;
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
                && (clock.millis() - lastTickMs) > staleThreshold.toMillis();

        String status;
        if (!receivedTicks) {
            status = "NO_TICKS_YET";
        } else if (stale) {
            status = "STALLED";
        } else {
            status = "ACTIVE";
        }

        boolean isUp = receivedTicks && !stale;

        if (!isUp && alertManager != null) {
            alertManager.warning("market-data", "Market data " + status + " (ticks=" + totalTicks + ")");
        }

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
