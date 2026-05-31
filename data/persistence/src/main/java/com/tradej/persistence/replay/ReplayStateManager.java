package com.tradej.persistence.replay;

/**
 * Lifecycle hook for isolating stateful pipeline components during replay.
 *
 * <p>Before replay begins, {@link #beforeReplay()} is called, giving implementors
 * a chance to snapshot mutable state (e.g., portfolio allocations, net positions,
 * open trades, candle buckets). After replay completes (or fails), {@link #afterReplay()}
 * is called to restore the snapshot, preventing replay events from corrupting
 * live pipeline state (AD-02).
 *
 * <p>The {@link #NOOP} instance is a no-op implementation used when state isolation
 * is not required.
 */
public interface ReplayStateManager {

    /**
     * Called once before any replay events are published.
     * Implementations should snapshot current pipeline state.
     */
    void beforeReplay();

    /**
     * Called once after all replay events have been published (or after a failure).
     * Implementations should restore the snapshot taken in {@link #beforeReplay()}.
     */
    void afterReplay();

    /** No-op implementation that performs no state management. */
    ReplayStateManager NOOP = new ReplayStateManager() {
        @Override
        public void beforeReplay() {}

        @Override
        public void afterReplay() {}
    };
}
