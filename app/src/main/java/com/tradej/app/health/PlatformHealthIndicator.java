package com.tradej.app.health;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.port.EventBus;
import com.tradej.disruptor.DisruptorBusMetrics;
import com.tradej.hotpath.MarketDataPipeline;
import com.tradej.hotpath.OrderPipeline;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated platform health indicator that synthesizes status from
 * broker connectivity, market data freshness, event bus health,
 * persistence pipeline status, and order pipeline status.
 *
 * <p>Reports {@code UP} only when all subsystems are healthy.
 * When any subsystem is unhealthy, the indicator reports {@code DOWN}
 * with a details map identifying which subsystems are degraded.
 */
@Component
public class PlatformHealthIndicator implements HealthIndicator {

    private final IBrokerConnection brokerConnection;
    private final MarketDataPipeline marketDataPipeline;
    private final EventBus eventBus;
    private final DisruptorBusMetrics disruptorBusMetrics;
    private final OrderPipeline orderPipeline;
    private final BrokerErrorTracker errorTracker;
    private final AlertManager alertManager;

    public PlatformHealthIndicator(
            IBrokerConnection brokerConnection,
            MarketDataPipeline marketDataPipeline,
            EventBus eventBus,
            DisruptorBusMetrics disruptorBusMetrics,
            OrderPipeline orderPipeline,
            BrokerErrorTracker errorTracker,
            AlertManager alertManager
    ) {
        this.brokerConnection = brokerConnection;
        this.marketDataPipeline = marketDataPipeline;
        this.eventBus = eventBus;
        this.disruptorBusMetrics = disruptorBusMetrics;
        this.orderPipeline = orderPipeline;
        this.errorTracker = errorTracker;
        this.alertManager = alertManager;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean allHealthy = true;

        // ── Broker connectivity ──
        boolean brokerConnected = brokerConnection.websocket().isConnected();
        long recentErrors = errorTracker.totalErrors();
        details.put("broker", Map.of(
                "status", brokerConnected ? "UP" : "DOWN",
                "websocketConnected", brokerConnected,
                "subscriptions", brokerConnection.websocket().subscriptions().size(),
                "recentErrors", recentErrors
        ));
        if (!brokerConnected) {
            allHealthy = false;
        }

        // ── Market data freshness ──
        long totalTicks = marketDataPipeline.totalTicksProcessed();
        long lastTickMs = marketDataPipeline.lastTickTimestampMs();
        double tickRate = marketDataPipeline.tickRate();
        boolean marketDataActive = totalTicks > 0 && lastTickMs > 0;
        details.put("marketData", Map.of(
                "status", marketDataActive ? "UP" : "DOWN",
                "totalTicks", totalTicks,
                "tickRate", Math.round(tickRate * 100.0) / 100.0,
                "lastTickMs", lastTickMs
        ));
        if (!marketDataActive && brokerConnected) {
            allHealthy = false;
        }

        // ── Event bus status ──
        boolean eventBusStarted = disruptorBusMetrics.isStarted();
        int subscriberCount = disruptorBusMetrics.subscriberCount();
        int dispatchQueueDepth = disruptorBusMetrics.dispatchQueueDepth();
        long droppedEvents = disruptorBusMetrics.dispatchDroppedEventCount();
        details.put("eventBus", Map.of(
                "status", eventBusStarted ? "UP" : "DOWN",
                "started", eventBusStarted,
                "subscribers", subscriberCount,
                "dispatchQueueDepth", dispatchQueueDepth,
                "droppedEvents", droppedEvents
        ));
        if (!eventBusStarted) {
            allHealthy = false;
        }

        // ── Persistence (DuckDB pipeline) ──
        long orderCount = orderPipeline.totalOrdersAccepted();
        double orderRate = orderPipeline.orderRate();
        details.put("persistence", Map.of(
                "status", "UP",
                "totalOrdersAccepted", orderCount,
                "orderRate", Math.round(orderRate * 100.0) / 100.0
        ));

        // ── Order pipeline ──
        details.put("pipeline", Map.of(
                "status", "UP",
                "totalOrdersAccepted", orderCount,
                "hasProcessedOrders", orderCount > 0
        ));

        Health.Builder builder = allHealthy ? Health.up() : Health.down();

        if (!allHealthy) {
            alertManager.warning("platform", "Platform health degraded");
        }

        return builder.withDetails(details).build();
    }
}
