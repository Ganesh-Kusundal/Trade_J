package com.tradej.core.domain.runtime;

public final class RuntimeBusHolder {
    private volatile RuntimeBus mode = RuntimeBus.SIMPLE;

    public RuntimeBus mode() {
        return mode;
    }

    public void setMode(RuntimeBus newMode) {
        mode = newMode == null ? RuntimeBus.SIMPLE : newMode;
    }
}
