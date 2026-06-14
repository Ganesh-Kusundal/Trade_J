package com.tradej.core.domain.port;

/**
 * Contract for any stateful service that participates in replay.
 * <p>
 * Implementing {@code Replayable} codifies the snapshot/restore protocol that
 * the replay orchestrator depends on. Any service that holds mutable per-run
 * state and is touched by the event bus should implement this interface.
 * <p>
 * The replay orchestrator uses this contract to refuse to run if the
 * registry of replayable services is incomplete.
 */
public interface Replayable {
    /** Capture the service's current state. Must be cheap and side-effect-free. */
    Object snapshot();

    /** Restore the service to a previously captured state. */
    void restore(Object snapshot);

    /** Stable identifier for diagnostics. */
    default String replayableId() { return getClass().getSimpleName(); }
}
