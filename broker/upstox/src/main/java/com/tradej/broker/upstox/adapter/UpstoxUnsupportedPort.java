package com.tradej.broker.upstox.adapter;

/**
 * Base for Upstox ports that are not supported by the public API.
 */
public abstract class UpstoxUnsupportedPort {
    protected static UnsupportedOperationException unsupported(String portName) {
        return new UnsupportedOperationException(
                "Upstox analytics-only mode does not support " + portName
                        + " — use a trading access token (analytics-only=false) or switch broker");
    }
}
