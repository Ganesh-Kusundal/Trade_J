package com.tradej.gateway.transport;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Spring WebSocket adapter for {@link WebSocketTransport}.
 * Wraps a Spring {@link WebSocketSession} behind the transport-agnostic interface.
 */
public final class SpringWebSocketTransport implements WebSocketTransport {

    private final WebSocketSession session;

    public SpringWebSocketTransport(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public void sendBinary(byte[] data) throws Exception {
        synchronized (session) {
            session.sendMessage(new BinaryMessage(data));
        }
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public String id() {
        return session.getId();
    }

    @Override
    public void close() throws Exception {
        session.close();
    }

    public WebSocketSession unwrap() {
        return session;
    }
}
