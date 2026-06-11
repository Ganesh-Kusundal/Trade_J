package com.tradej.core.domain.runtime;

public enum RuntimeBus {
    SIMPLE,
    DISRUPTOR;

    public static RuntimeBus fromString(String value) {
        if (value == null || value.isBlank()) {
            return SIMPLE;
        }
        return RuntimeBus.valueOf(value.trim().toUpperCase());
    }
}
