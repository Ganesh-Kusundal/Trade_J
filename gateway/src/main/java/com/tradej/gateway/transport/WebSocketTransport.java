package com.tradej.gateway.transport;

/**
 * Transport-agnostic abstraction for a WebSocket session.
 * Decouples the gateway routing and codec logic from any specific WebSocket library.
 */
public interface WebSocketTransport {

    /** Send a binary frame to the remote endpoint. */
    void sendBinary(byte[] data) throws Exception;

    /** Whether the underlying connection is still open. */
    boolean isOpen();

    /** Unique identifier for this session. */
    String id();

    /** Close the connection. */
    void close() throws Exception;
}
