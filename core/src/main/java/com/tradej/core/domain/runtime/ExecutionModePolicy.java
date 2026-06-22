package com.tradej.core.domain.runtime;

import com.tradej.core.domain.config.TradeDefaults;

import java.util.Objects;

/**
 * Central contract for behavior that changes by runtime mode.
 */
public record ExecutionModePolicy(RuntimeMode mode) {

    public enum HotPathWaitProfile {
        LOW_LATENCY,
        DETERMINISTIC
    }

    public ExecutionModePolicy {
        mode = Objects.requireNonNullElse(mode, TradeDefaults.RUNTIME_MODE);
    }

    public static ExecutionModePolicy forMode(RuntimeMode mode) {
        return new ExecutionModePolicy(mode);
    }

    public static ExecutionModePolicy live() {
        return new ExecutionModePolicy(RuntimeMode.LIVE);
    }

    public boolean permitsBrokerSideEffects() {
        return mode == RuntimeMode.LIVE;
    }

    public boolean usesSimulatedExecution() {
        return mode == RuntimeMode.REPLAY || mode == RuntimeMode.BACKTEST;
    }

    public boolean usesBacktestEngine() {
        return mode == RuntimeMode.BACKTEST;
    }

    public boolean usesDeterministicClock() {
        return mode == RuntimeMode.REPLAY || mode == RuntimeMode.BACKTEST;
    }

    public boolean permitsHistoricalReplayEndpoints() {
        return mode != RuntimeMode.LIVE;
    }

    public boolean permitsFallbackInfrastructure() {
        return mode != RuntimeMode.LIVE;
    }

    public HotPathWaitProfile hotPathWaitProfile() {
        return mode == RuntimeMode.LIVE
                ? HotPathWaitProfile.LOW_LATENCY
                : HotPathWaitProfile.DETERMINISTIC;
    }
}
