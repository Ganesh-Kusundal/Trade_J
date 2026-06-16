package com.tradej.app.health;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.execution.service.TradingCircuitBreaker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

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
        Map<String, Boolean> allCircuitStates = CircuitBreaker.snapshotAllCircuitStates();
        boolean anyBrokerCircuitOpen = allCircuitStates.values().stream().anyMatch(Boolean::booleanValue);
        boolean isUp = brokerConnection.websocket().isConnected()
                && !tradingCircuitBreaker.isOpen()
                && !anyBrokerCircuitOpen;
        Health.Builder builder = isUp ? Health.up() : Health.down();

        builder
                .withDetail("broker", brokerType)
                .withDetail("websocketConnected", brokerConnection.websocket().isConnected())
                .withDetail("circuitBreakerOpen", tradingCircuitBreaker.isOpen())
                .withDetail("subscriptions", brokerConnection.websocket().subscriptions().size());

        if (!allCircuitStates.isEmpty()) {
            builder.withDetail("brokerCircuitStates", allCircuitStates);
        }

        long totalErrors = errorTracker.totalErrors();
        builder.withDetail("errorCount", totalErrors);
        if (totalErrors > 0) {
            builder.withDetail("errorsBySource", errorTracker.errorsBySource());
            builder.withDetail("lastErrorAt", Instant.ofEpochMilli(errorTracker.lastErrorTimestampMs()).toString());
            builder.withDetail("lastErrorSource", errorTracker.lastErrorSource());
            builder.withDetail("lastErrorDetail", errorTracker.lastErrorDetail());
        }

        if (!isUp) {
            String reason;
            if (!brokerConnection.websocket().isConnected()) {
                reason = "WebSocket disconnected";
            } else if (tradingCircuitBreaker.isOpen()) {
                reason = "Trading circuit breaker open";
            } else {
                reason = "Broker circuit(s) open: " + allCircuitStates.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .toList();
            }
            alertManager.critical("broker-" + brokerType, reason);
        }

        return builder.build();
    }
}
