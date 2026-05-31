package com.tradej.app.config;

import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.TradingClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    TradingClock tradingClock(Clock clock) {
        return new LiveTradingClock(clock);
    }

    @Bean
    EventMetadataFactory eventMetadataFactory(TradingClock tradingClock) {
        return new EventMetadataFactory(tradingClock);
    }
}
