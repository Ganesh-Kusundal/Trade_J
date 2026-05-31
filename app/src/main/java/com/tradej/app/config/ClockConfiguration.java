package com.tradej.app.config;

import com.tradej.core.domain.event.EventMetadataFactory;
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
    EventMetadataFactory eventMetadataFactory(Clock clock) {
        return new EventMetadataFactory(clock);
    }
}
