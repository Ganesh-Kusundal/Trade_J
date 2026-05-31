package com.tradej.app.config;

import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.ReplayTradingClock;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.core.domain.event.EventMetadataFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Instant;

@Configuration
public class TimeConfiguration {

    @Bean
    @org.springframework.context.annotation.Primary
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
