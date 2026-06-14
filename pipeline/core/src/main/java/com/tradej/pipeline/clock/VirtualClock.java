package com.tradej.pipeline.clock;

import com.tradej.core.domain.market.CandleBucketPolicy;
import com.tradej.core.domain.time.TradingClock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-precision Clock abstraction supporting deterministic replay, virtual time,
 * backtest speed control, and seamless switching between Live, Replay, and Backtesting modes.
 * <p>
 * Implements {@link TradingClock} so it can be used as a drop-in for the OMS/MatchingEngine
 * clock in any context that needs determinism. The pipeline-specific methods
 * ({@link #enterLiveMode()}, {@link #enterReplayMode()}, speed control, pacing) are
 * additional behaviors layered on top of the {@code TradingClock} contract.
 */
public final class VirtualClock implements TradingClock {

    public enum Mode {
        LIVE,
        REPLAY
    }

    private volatile Mode mode;
    private final AtomicLong virtualTimeMs = new AtomicLong();
    /**
     * Speed multiplier for backtest/replay wall-clock pacing.
     * 0 = instant (no pacing), 1 = real-time, N = Nx real-time.
     */
    private final AtomicLong speedMultiplier = new AtomicLong(1);

    public VirtualClock(Mode mode) {
        this.mode = mode;
        this.virtualTimeMs.set(System.currentTimeMillis());
    }

    public long currentTimeMillis() {
        if (mode == Mode.LIVE) {
            return System.currentTimeMillis();
        }
        return virtualTimeMs.get();
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(currentTimeMillis());
    }

    @Override
    public LocalDateTime now() {
        return LocalDateTime.ofInstant(instant(), CandleBucketPolicy.IST);
    }

    /**
     * Returns the current virtual time for clock-driven operations.
     * In LIVE mode this is {@link System#currentTimeMillis()}.
     * In REPLAY mode this is the advanced virtual time.
     */
    public long virtualTimeMillis() {
        return virtualTimeMs.get();
    }

    /**
     * Advances replay time monotonically — never moves backward during a replay session.
     */
    public void advanceVirtualTimeMs(long timestampMs) {
        if (mode == Mode.LIVE) {
            return;
        }
        virtualTimeMs.updateAndGet(current -> Math.max(current, timestampMs));
    }

    /**
     * Sets the speed multiplier for backtest/replay.
     *
     * @param multiplier 0 = instant (no pacing), 1 = real-time, N = Nx speed
     */
    public void setSpeed(long multiplier) {
        speedMultiplier.set(Math.max(0, multiplier));
    }

    /**
     * Returns the current speed multiplier.
     */
    public long speedMultiplier() {
        return speedMultiplier.get();
    }

    /**
     * Returns the wall-clock delay in milliseconds that the pacing loop should
     * sleep to maintain the configured speed ratio between virtual time and real time.
     *
     * @param virtualTimeDeltaMs the number of virtual milliseconds that have elapsed
     * @return sleep duration in milliseconds, or 0 for instant mode
     */
    public long paceSleepMs(long virtualTimeDeltaMs) {
        long multiplier = speedMultiplier.get();
        if (multiplier == 0) {
            return 0; // instant mode
        }
        long sleep = virtualTimeDeltaMs / multiplier;
        return Math.max(0, sleep);
    }

    public void enterReplayMode() {
        mode = Mode.REPLAY;
        virtualTimeMs.set(System.currentTimeMillis());
    }

    public void enterLiveMode() {
        mode = Mode.LIVE;
    }

    public Mode getMode() {
        return mode;
    }
}
