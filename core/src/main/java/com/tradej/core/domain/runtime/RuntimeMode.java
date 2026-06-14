package com.tradej.core.domain.runtime;

/**
 * Operating mode for the trading runtime. Controls whether broker side effects
 * are permitted and how historical replay is routed.
 */
public enum RuntimeMode {
    /** Live market connectivity with real broker order placement. */
    LIVE,
    /** User-facing paper trading: live data, simulated fills, no real money. */
    PAPER,
    /** Replay domain events from journal without broker side effects. */
    REPLAY,
    /** Simulated fills from historical data without broker connectivity. */
    BACKTEST;

    public boolean allowsBrokerOrders() {
        return this == LIVE;
    }

    /** Uses in-process matching instead of broker REST placement. */
    public boolean usesSimulatedExecution() {
        return this == PAPER || this == REPLAY || this == BACKTEST;
    }

    /** Clock-driven historical simulation with fill model. */
    public boolean usesBacktestEngine() {
        return this == BACKTEST;
    }

    /**
     * Returns true when the mode is a non-LIVE, user-facing trading mode that the
     * LIVE/PAPER toggle should expose. REPLAY and BACKTEST are dev/test only and
     * must be set via Spring profile, not the UI toggle.
     */
    public boolean isUserToggleable() {
        return this == LIVE || this == PAPER;
    }

    public static RuntimeMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return LIVE;
        }
        return RuntimeMode.valueOf(value.trim().toUpperCase());
    }
}
