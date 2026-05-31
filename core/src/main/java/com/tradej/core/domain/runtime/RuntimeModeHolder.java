package com.tradej.core.domain.runtime;

/**
 * Thread-safe holder for the active {@link RuntimeMode}.
 */
public final class RuntimeModeHolder {

    private volatile RuntimeMode mode = RuntimeMode.LIVE;

    public RuntimeMode mode() {
        return mode;
    }

    public void setMode(RuntimeMode newMode) {
        mode = newMode;
    }

    public boolean allowsBrokerOrders() {
        return mode.allowsBrokerOrders();
    }
}
