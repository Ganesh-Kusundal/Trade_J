package com.tradej.broker.dhan.reactive.config;

import java.time.Duration;

/**
 * Configuration settings for reactive Dhan connection.
 */
public record DhanReactiveConnectionSettings(
    String clientId,
    String apiSecret,
    boolean sandbox,
    int maxConnections,
    Duration connectionTimeout,
    Duration responseTimeout
) {
    
    public DhanReactiveConnectionSettings {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalArgumentException("apiSecret must not be blank");
        }
    }
    
    public String baseUrl() {
        return sandbox 
            ? "https://api.dhan.co/v2" 
            : "https://api.dhan.co/v2";
    }
    
    public String tokenUrl() {
        return baseUrl() + "/auth/token";
    }
    
    public String websocketUrl() {
        return sandbox 
            ? "wss://api.dhan.co/v2/feed"
            : "wss://api.dhan.co/v2/feed";
    }
}
