package com.tradej.app.config;

import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.time.ReplayTradingClock;
import com.tradej.core.domain.time.TradingClock;
import com.tradej.core.domain.event.EventMetadataFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration
public class TimeConfiguration {

    @Bean
    public TradingClock tradingClock(RuntimeModeHolder runtimeModeHolder) {
        if (runtimeModeHolder.mode() == RuntimeMode.REPLAY || runtimeModeHolder.mode() == RuntimeMode.BACKTEST) {
            // Default start time for replay if not explicitly set, 
            // though in practice the ReplayController will manage this.
            return new ReplayTradingClock(Instant.EPOCH);
        }
        return new LiveTradingClock();
    }

    @Bean
    public EventMetadataFactory eventMetadataFactory(TradingClock tradingClock) {
        return new EventMetadataFactory(tradingClock);
    }
}
