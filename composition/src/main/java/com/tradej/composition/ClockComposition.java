package com.tradej.composition;

import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.ReplayTradingClock;
import com.tradej.core.domain.time.TradingClock;

import java.time.Clock;
import java.time.Instant;

/**
 * Composition root for clock and time-related concerns.
 * Provides framework-agnostic factory methods for creating trading clocks
 * and event metadata factories.
 */
public final class ClockComposition {

    private final Clock clock;
    private final TradingClock tradingClock;
    private final EventMetadataFactory eventMetadataFactory;

    private ClockComposition(Clock clock, TradingClock tradingClock, EventMetadataFactory eventMetadataFactory) {
        this.clock = clock;
        this.tradingClock = tradingClock;
        this.eventMetadataFactory = eventMetadataFactory;
    }

    public static ClockComposition live() {
        Clock clock = Clock.systemDefaultZone();
        TradingClock tradingClock = new LiveTradingClock(clock);
        return new ClockComposition(clock, tradingClock, new EventMetadataFactory(tradingClock));
    }

    public static ClockComposition replay() {
        Clock clock = Clock.fixed(Instant.EPOCH, java.time.ZoneId.of("Asia/Kolkata"));
        TradingClock tradingClock = new ReplayTradingClock(Instant.EPOCH);
        return new ClockComposition(clock, tradingClock, new EventMetadataFactory(tradingClock));
    }

    public static ClockComposition live(Clock clock) {
        TradingClock tradingClock = new LiveTradingClock(clock);
        return new ClockComposition(clock, tradingClock, new EventMetadataFactory(tradingClock));
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
