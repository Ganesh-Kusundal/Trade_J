package com.tradej.broker.upstox.websocket;

/**
 * Connection state for monitoring and diagnostics.
 */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR,
    FAILED
}
