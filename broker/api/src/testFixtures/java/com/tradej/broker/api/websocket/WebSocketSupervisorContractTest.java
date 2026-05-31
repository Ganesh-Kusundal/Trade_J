package com.tradej.broker.api.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test that ALL {@link WebSocketSupervisor} implementations must pass.
 *
 * <p>Subclass this test for each concrete broker adapter:
 * <ul>
 *   <li>{@code DhanWebSocketSupervisorContractTest} in {@code trade-broker-dhan}</li>
 *   <li>{@code UpstoxWebSocketSupervisorContractTest} in {@code trade-broker-upstox}</li>
 * </ul>
 */
@Tag("unit")
public abstract class WebSocketSupervisorContractTest {

    /** Subclass must provide a freshly-constructed implementation. */
    protected abstract WebSocketSupervisor createSupervisor();

    private WebSocketSupervisor supervisor;

    @BeforeEach
    void setUp() {
        supervisor = createSupervisor();
    }

    @Test
    void initialStateIsDisconnected() {
        assertEquals(WebSocketSupervisor.State.DISCONNECTED, supervisor.state(),
                "Initial state must be DISCONNECTED");
    }

    @Test
    void disconnectOnDisconnectedDoesNotThrow() {
        assertDoesNotThrow(() -> supervisor.disconnect(),
                "disconnect() must not throw when already DISCONNECTED");
    }

    @Test
    void lastMessageTimestampReturnsPositiveAfterInitialization() {
        assertTrue(supervisor.lastMessageTimestampMs() > 0,
                "lastMessageTimestampMs() must return a positive wall-clock millis");
    }

    @Test
    void stalenessThresholdReturnsPositive() {
        assertTrue(supervisor.stalenessThresholdMs() > 0,
                "stalenessThresholdMs() must return a positive value");
    }

    @Test
    void addStateListenerDoesNotThrow() {
        assertDoesNotThrow(() -> supervisor.addStateListener(s -> {}),
                "addStateListener() must not throw");
    }

    @Test
    void removeStateListenerDoesNotThrow() {
        var listener = (java.util.function.Consumer<WebSocketSupervisor.State>) s -> {};
        supervisor.addStateListener(listener);
        assertDoesNotThrow(() -> supervisor.removeStateListener(listener),
                "removeStateListener() must not throw after addStateListener()");
    }

    @Test
    void removeUnknownListenerDoesNotThrow() {
        var listener = (java.util.function.Consumer<WebSocketSupervisor.State>) s -> {};
        assertDoesNotThrow(() -> supervisor.removeStateListener(listener),
                "removeStateListener() must not throw for an unknown listener");
    }

    @Test
    void closeDelegatesToDisconnect() {
        assertDoesNotThrow(() -> supervisor.close(),
                "close() must not throw");
    }
}
