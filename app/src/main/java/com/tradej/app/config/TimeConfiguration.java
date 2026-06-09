package com.tradej.app.config;

import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.ReplayTradingClock;
import com.tradej.core.domain.time.TradingClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;

/**
 * Unified time and clock configuration.
 *
 * <p>Provides a {@link Clock} bean, profile-aware {@link TradingClock} beans
 * (live vs replay), and the shared {@link EventMetadataFactory}.
 */
@Configuration
public class TimeConfiguration {

    @Bean
    @Profile("!replay")
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @Primary
    @Profile("!replay")
    public TradingClock liveTradingClock() {
        return new LiveTradingClock();
    }

    @Bean
    @Profile("replay")
    public TradingClock replayTradingClock() {
        return new ReplayTradingClock(Instant.EPOCH);
    }

    @Bean
    public EventMetadataFactory eventMetadataFactory(TradingClock tradingClock) {
        return new EventMetadataFactory(tradingClock);
    }
}
