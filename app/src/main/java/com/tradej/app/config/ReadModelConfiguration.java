package com.tradej.app.config;

import com.tradej.execution.readmodel.ReadModelStore;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Read-model and projection-backed reconciliation beans.
 */
@Configuration
public class ReadModelConfiguration {

    @Bean
    ReadModelStore readModelStore() {
        return new ReadModelStore();
    }

    @Bean
    EventSourcedNetPositionProvider eventSourcedNetPositionProvider() {
        return new EventSourcedNetPositionProvider();
    }
}
