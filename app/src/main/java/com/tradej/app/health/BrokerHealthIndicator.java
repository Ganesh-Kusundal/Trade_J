package com.tradej.app.health;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.execution.service.TradingCircuitBreaker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BrokerHealthIndicator implements HealthIndicator {
    private final IBrokerConnection brokerConnection;
    private final TradingCircuitBreaker tradingCircuitBreaker;
    private final BrokerErrorTracker errorTracker;
    private final AlertManager alertManager;
    private final String brokerType;

    public BrokerHealthIndicator(
            IBrokerConnection brokerConnection,
            TradingCircuitBreaker tradingCircuitBreaker,
            BrokerErrorTracker errorTracker,
            AlertManager alertManager,
            Environment environment
    ) {
        this.brokerConnection = brokerConnection;
        this.tradingCircuitBreaker = tradingCircuitBreaker;
        this.errorTracker = errorTracker;
        this.alertManager = alertManager;
        this.brokerType = environment.getProperty("trade.broker-type", "dhan");
    }

    @Override
    public Health health() {
        boolean isUp = brokerConnection.websocket().isConnected() && !tradingCircuitBreaker.isOpen();
        Health.Builder builder = isUp ? Health.up() : Health.down();

        builder
                .withDetail("broker", brokerType)
                .withDetail("websocketConnected", brokerConnection.websocket().isConnected())
                .withDetail("circuitBreakerOpen", tradingCircuitBreaker.isOpen())
                .withDetail("subscriptions", brokerConnection.websocket().subscriptions().size());

        long totalErrors = errorTracker.totalErrors();
        builder.withDetail("errorCount", totalErrors);
        if (totalErrors > 0) {
            builder.withDetail("errorsBySource", errorTracker.errorsBySource());
            builder.withDetail("lastErrorAt", Instant.ofEpochMilli(errorTracker.lastErrorTimestampMs()).toString());
            builder.withDetail("lastErrorSource", errorTracker.lastErrorSource());
            builder.withDetail("lastErrorDetail", errorTracker.lastErrorDetail());
        }

        if (!isUp) {
            String reason = !brokerConnection.websocket().isConnected()
                    ? "WebSocket disconnected"
                    : "Circuit breaker open";
            alertManager.critical("broker-" + brokerType, reason);
        }

        return builder.build();
    }
}
