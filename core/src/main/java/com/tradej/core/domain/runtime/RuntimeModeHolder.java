package com.tradej.core.domain.runtime;

import com.tradej.core.domain.config.TradeDefaults;

/**
 * Thread-safe holder for the active {@link RuntimeMode}.
 */
public final class RuntimeModeHolder {

    private volatile RuntimeMode mode = TradeDefaults.RUNTIME_MODE;

    public RuntimeMode mode() {
        return mode;
    }

    public void setMode(RuntimeMode newMode) {
        mode = newMode == null ? TradeDefaults.RUNTIME_MODE : newMode;
    }

    public boolean allowsBrokerOrders() {
        return mode.allowsBrokerOrders();
    }

    public ExecutionModePolicy policy() {
        return ExecutionModePolicy.forMode(mode);
    }
}
