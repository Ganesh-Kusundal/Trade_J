package com.tradej.composition;

import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.ReplayTradingClock;
import com.tradej.core.domain.time.TradingClock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Clock composition root — owns the canonical {@link Clock}, {@link TradingClock}, and
 * {@link EventMetadataFactory} for both the Spring app path and the CLI / replay path.
 *
 * <p>Three factory methods cover the only call sites that exist in production:
 * <ul>
 *   <li>{@link #live()} — system default zone, system clock (live trading).</li>
 *   <li>{@link #replay()} — fixed at {@link Instant#EPOCH} in IST (deterministic replay).</li>
 *   <li>{@link #live(Clock)} — caller-supplied clock (tests and tooling).</li>
 * </ul>
 *
 * <p>Mirrors the bean wiring that {@code app/.../config/RuntimeAndStartupConfiguration.java:71-93}
 * historically performed. Spring consumers obtain a {@code ClockComposition} via a
 * {@code @Bean} method that returns the result of {@link #live()} or {@link #replay()}.
 */
public final class ClockComposition {

    private final Clock clock;
    private final TradingClock tradingClock;
    private final EventMetadataFactory eventMetadataFactory;

    private ClockComposition(Clock clock, TradingClock tradingClock, EventMetadataFactory factory) {
        this.clock = clock;
        this.tradingClock = tradingClock;
        this.eventMetadataFactory = factory;
    }

    /**
     * Live trading clock — system default zone, system clock, IST trading clock.
     */
    public static ClockComposition live() {
        Clock clock = Clock.systemDefaultZone();
        TradingClock tradingClock = new LiveTradingClock();
        EventMetadataFactory factory = new EventMetadataFactory(tradingClock);
        return new ClockComposition(clock, tradingClock, factory);
    }

    /**
     * Replay clock — fixed at {@link Instant#EPOCH} in IST. The {@link ReplayTradingClock}
     * allows the test or replay engine to advance time deterministically.
     */
    public static ClockComposition replay() {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("Asia/Kolkata"));
        TradingClock tradingClock = new ReplayTradingClock(Instant.EPOCH);
        EventMetadataFactory factory = new EventMetadataFactory(tradingClock);
        return new ClockComposition(clock, tradingClock, factory);
    }

    /**
     * Live trading clock with a caller-supplied {@link Clock}. Useful for tests that
     * need to inject a fixed or mutable clock without touching system time.
     *
     * @param clock non-null caller-supplied clock
     * @throws NullPointerException if {@code clock} is null
     */
    public static ClockComposition live(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        TradingClock tradingClock = new LiveTradingClock(clock);
        EventMetadataFactory factory = new EventMetadataFactory(tradingClock);
        return new ClockComposition(clock, tradingClock, factory);
    }

    public Clock clock() {
        return clock;
    }

    public TradingClock tradingClock() {
        return tradingClock;
    }

    public EventMetadataFactory eventMetadataFactory() {
        return eventMetadataFactory;
    }
}
