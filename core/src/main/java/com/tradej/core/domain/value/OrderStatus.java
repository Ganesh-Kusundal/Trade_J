package com.tradej.core.domain.value;

public enum OrderStatus {
    PENDING,
    OPEN,
    PART_TRADED,
    TRADED,
    CANCELLED,
    REJECTED,
    UNKNOWN;

    /**
     * Returns true if this status represents an order that is still in flight
     * (has been accepted by the exchange but not yet fully filled or cancelled).
     */
    public boolean isActive() {
        return this == PENDING || this == OPEN || this == PART_TRADED;
    }

    /**
     * Returns true if this status represents a final, non-reversible state
     * (the order lifecycle has concluded).
     */
    public boolean isTerminal() {
        return this == TRADED || this == CANCELLED || this == REJECTED;
    }

    /**
     * Returns true if the order was explicitly rejected by the exchange or broker.
     */
    public boolean isRejected() {
        return this == REJECTED;
    }
}
