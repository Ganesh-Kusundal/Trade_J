package com.tradej.app.service;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.execution.service.TradingCircuitBreaker;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application service for admin operations.
 * Encapsulates broker connection access for admin/operational endpoints.
 */
@Service
public class AdminApplicationService {

    private final IBrokerConnection brokerConnection;
    private final TradingCircuitBreaker tradingCircuitBreaker;

    public AdminApplicationService(IBrokerConnection brokerConnection, TradingCircuitBreaker tradingCircuitBreaker) {
        this.brokerConnection = brokerConnection;
        this.tradingCircuitBreaker = tradingCircuitBreaker;
    }

    public Map<String, Object> getRuntimeStatus(boolean catalogLoaded, int catalogSize,
                                                 boolean brokerPreflightPassed, boolean startupCompleted) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("websocketConnected", brokerConnection.websocket().isConnected());
        status.put("circuitBreakerOpen", tradingCircuitBreaker.isOpen());
        status.put("subscriptions", brokerConnection.websocket().subscriptions().size());
        status.put("catalogLoaded", catalogLoaded);
        status.put("catalogSize", catalogSize);
        status.put("brokerPreflightPassed", brokerPreflightPassed);
        status.put("startupCompleted", startupCompleted);
        return status;
    }

    public Map<String, Object> setKillSwitch(boolean enabled) {
        boolean acknowledged = brokerConnection.orders().setKillSwitch(enabled);
        return Map.of("enabled", enabled, "acknowledged", acknowledged);
    }
}
