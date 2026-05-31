package com.tradej.broker.upstox.config;

/**
 * Upstox API environment selection.
 */
public enum UpstoxApiEnvironment {
    LIVE("https://api.upstox.com/v2"),
    SANDBOX("https://sandbox-api.upstox.com/v2");

    private final String baseUrl;

    UpstoxApiEnvironment(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }
}
