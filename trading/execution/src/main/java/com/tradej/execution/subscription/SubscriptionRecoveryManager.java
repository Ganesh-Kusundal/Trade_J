package com.tradej.execution.subscription;

import java.util.Objects;

/**
 * Re-applies the desired subscription snapshot after a broker WebSocket reconnect.
 */
public final class SubscriptionRecoveryManager {

    private final SubscriptionManager manager;

    public SubscriptionRecoveryManager(SubscriptionManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    public void recoverAfterReconnect() {
        manager.reconcileAfterReconnect();
    }
}
