package com.tradej.app.health;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.execution.service.TradingCircuitBreaker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class BrokerHealthIndicator implements HealthIndicator {
    private final IBrokerConnection brokerConnection;
    private final TradingCircuitBreaker tradingCircuitBreaker;

    public BrokerHealthIndicator(IBrokerConnection brokerConnection, TradingCircuitBreaker tradingCircuitBreaker) {
        this.brokerConnection = brokerConnection;
        this.tradingCircuitBreaker = tradingCircuitBreaker;
    }

    @Override
    public Health health() {
        Health.Builder builder = brokerConnection.websocket().isConnected() && !tradingCircuitBreaker.isOpen()
                ? Health.up()
                : Health.down();
        return builder
                .withDetail("broker", "dhan")
                .withDetail("websocketConnected", brokerConnection.websocket().isConnected())
                .withDetail("circuitBreakerOpen", tradingCircuitBreaker.isOpen())
                .withDetail("subscriptions", brokerConnection.websocket().subscriptions().size())
                .build();
    }
}
