package com.tradej.core.domain.oms;

/**
 * Deterministic lifecycle states for the OMS state machine.
 * Transitions are defined in {@link OrderStateMachine}.
 */
public enum LifecycleState {
    NEW,
    PENDING_SUBMIT,
    SUBMITTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCEL_PENDING,
    CANCELLED,
    REJECTED,
    EXPIRED;

    /**
     * Returns true if this state is a terminal (final) state.
     * Terminal states: FILLED, CANCELLED, REJECTED, EXPIRED.
     */
    public boolean isFinal() {
        return switch (this) {
            case FILLED, CANCELLED, REJECTED, EXPIRED -> true;
            default -> false;
        };
    }
}
