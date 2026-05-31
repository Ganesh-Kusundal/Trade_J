package com.tradej.core.domain.time;

import com.tradej.core.domain.market.CandleBucketPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Implementation of {@link TradingClock} that uses the system clock.
 * Defaults to the IST (Asia/Kolkata) timezone as defined in {@link CandleBucketPolicy}.
 */
public class LiveTradingClock implements TradingClock {

    private final Clock clock;

    public LiveTradingClock() {
        this(Clock.system(CandleBucketPolicy.IST));
    }

    public LiveTradingClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant instant() {
        return clock.instant();
    }

    @Override
    public LocalDateTime now() {
        return LocalDateTime.ofInstant(instant(), clock.getZone());
    }
}
