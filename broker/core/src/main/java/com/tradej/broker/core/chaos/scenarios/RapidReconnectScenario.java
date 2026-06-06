package com.tradej.broker.core.chaos.scenarios;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.core.chaos.ChaosScenario;

/**
 * Rapidly connects and disconnects WebSocket to stress-test reconnection logic.
 */
public final class RapidReconnectScenario implements ChaosScenario {

    private final int cycles;

    public RapidReconnectScenario(int cycles) {
        this.cycles = cycles;
    }

    @Override
    public String name() { return "RapidReconnect"; }

    @Override
    public void apply(IBrokerConnection connection, ChaosContext context) {
        for (int i = 0; i < cycles; i++) {
            try {
                connection.websocket().connect();
                context.metrics().recordReconnectAttempt();
                Thread.sleep(100);
                connection.websocket().disconnect();
                context.log("Cycle " + (i + 1) + ": connect/disconnect OK");
            } catch (Exception ex) {
                context.log("Cycle " + (i + 1) + ": FAILED — " + ex.getMessage());
            }
        }
    }
}
