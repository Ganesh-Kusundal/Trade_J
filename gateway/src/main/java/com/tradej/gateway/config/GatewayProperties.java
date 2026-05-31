package com.tradej.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway configuration properties.
 */
@ConfigurationProperties(prefix = "tradej.gateway")
public class GatewayProperties {

    /** WebSocket endpoint path. */
    private String websocketPath = "/ws/gateway";

    /** Maximum WebSocket session idle timeout in milliseconds. */
    private long sessionTimeoutMs = 300_000L;

    /** Maximum binary message size. */
    private int maxBinaryMessageSize = 65536;

    public String websocketPath() {
        return websocketPath;
    }

    public void setWebsocketPath(String websocketPath) {
        this.websocketPath = websocketPath;
    }

    public long sessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    public void setSessionTimeoutMs(long sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public int maxBinaryMessageSize() {
        return maxBinaryMessageSize;
    }

    public void setMaxBinaryMessageSize(int maxBinaryMessageSize) {
        this.maxBinaryMessageSize = maxBinaryMessageSize;
    }
}
