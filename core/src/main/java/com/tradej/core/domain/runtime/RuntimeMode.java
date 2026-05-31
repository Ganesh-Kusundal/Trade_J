package com.tradej.core.domain.runtime;

/**
 * Operating mode for the trading runtime. Controls whether broker side effects
 * are permitted and how historical replay is routed.
 */
public enum RuntimeMode {
    /** Live market connectivity with real broker order placement. */
    LIVE,
    /** Replay domain events from journal without broker side effects. */
    REPLAY,
    /** Simulated fills from historical data without broker connectivity. */
    BACKTEST;

    public boolean allowsBrokerOrders() {
        return this == LIVE;
    }

    /** Uses in-process matching instead of broker REST placement. */
    public boolean usesSimulatedExecution() {
        return this == REPLAY || this == BACKTEST;
    }

    /** Clock-driven historical simulation with fill model. */
    public boolean usesBacktestEngine() {
        return this == BACKTEST;
    }

    public static RuntimeMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return LIVE;
        }
        return RuntimeMode.valueOf(value.trim().toUpperCase());
    }
}
