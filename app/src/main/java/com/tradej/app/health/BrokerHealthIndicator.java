package com.tradej.app.health;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.execution.service.TradingCircuitBreaker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "dhan", matchIfMissing = true)
public class BrokerHealthIndicator implements HealthIndicator {
    private final IBrokerConnection brokerConnection;
    private final TradingCircuitBreaker tradingCircuitBreaker;
    private final BrokerErrorTracker errorTracker;

    public BrokerHealthIndicator(
            IBrokerConnection brokerConnection,
            TradingCircuitBreaker tradingCircuitBreaker,
            BrokerErrorTracker errorTracker
    ) {
        this.brokerConnection = brokerConnection;
        this.tradingCircuitBreaker = tradingCircuitBreaker;
        this.errorTracker = errorTracker;
    }

    @Override
    public Health health() {
        boolean isUp = brokerConnection.websocket().isConnected() && !tradingCircuitBreaker.isOpen();
        Health.Builder builder = isUp ? Health.up() : Health.down();

        builder
                .withDetail("broker", "dhan")
                .withDetail("websocketConnected", brokerConnection.websocket().isConnected())
                .withDetail("circuitBreakerOpen", tradingCircuitBreaker.isOpen())
                .withDetail("subscriptions", brokerConnection.websocket().subscriptions().size());

        // Error tracking details
        long totalErrors = errorTracker.totalErrors();
        builder.withDetail("errorCount", totalErrors);
        if (totalErrors > 0) {
            builder.withDetail("errorsBySource", errorTracker.errorsBySource());
            builder.withDetail("lastErrorAt", Instant.ofEpochMilli(errorTracker.lastErrorTimestampMs()).toString());
            builder.withDetail("lastErrorSource", errorTracker.lastErrorSource());
            builder.withDetail("lastErrorDetail", errorTracker.lastErrorDetail());
        }

        return builder.build();
    }
}
