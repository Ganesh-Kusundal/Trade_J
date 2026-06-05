package com.tradej.gateway.websocket;

/**
 * Processes inbound replay control commands from gateway WebSocket clients.
 */
@FunctionalInterface
public interface GatewayReplayCommandProcessor {

    void processCommand(String jsonPayload);
}
