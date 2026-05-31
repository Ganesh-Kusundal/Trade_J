package com.tradej.core.domain.value;

public enum Side {
    BUY,
    SELL,
    LONG,
    SHORT,
    UNKNOWN;

    public boolean isBuySide() {
        return this == BUY || this == LONG;
    }
}
