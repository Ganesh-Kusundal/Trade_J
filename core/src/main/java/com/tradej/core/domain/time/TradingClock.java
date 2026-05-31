package com.tradej.core.domain.time;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Unified interface for time management in the Trade-J platform.
 * Supports both wall-clock time (Live) and controlled/backdated time (Replay/Backtest).
 * All local times are relative to the IST (Asia/Kolkata) timezone.
 */
public interface TradingClock {

    /**
     * Returns the current {@link Instant}.
     *
     * @return the current instant from this clock
     */
    Instant instant();

    /**
     * Returns the current date and time in the IST (Asia/Kolkata) timezone.
     *
     * @return the current local date-time from this clock
     */
    LocalDateTime now();

    /**
     * Returns the current time in milliseconds from the epoch.
     *
     * @return the current time in milliseconds
     */
    default long millis() {
        return instant().toEpochMilli();
    }
}
