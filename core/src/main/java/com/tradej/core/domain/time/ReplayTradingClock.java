package com.tradej.core.domain.time;

import com.tradej.core.domain.market.CandleBucketPolicy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link TradingClock} for deterministic replay and backtesting.
 * Time only advances when {@link #advanceTo(Instant)} is called.
 */
public class ReplayTradingClock implements TradingClock {

    private final AtomicReference<Instant> currentInstant;

    public ReplayTradingClock(Instant startInstant) {
        this.currentInstant = new AtomicReference<>(startInstant);
    }

    @Override
    public Instant instant() {
        return currentInstant.get();
    }

    @Override
    public LocalDateTime now() {
        return LocalDateTime.ofInstant(instant(), CandleBucketPolicy.IST);
    }

    /**
     * Advances the clock to the specified instant.
     *
     * @param newInstant the new current instant for this clock
     */
    public void advanceTo(Instant newInstant) {
        currentInstant.set(newInstant);
    }
}
