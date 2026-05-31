package com.tradej.app.config;

import com.tradej.core.domain.runtime.RuntimeModeHolder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Applies configured {@link com.tradej.core.domain.runtime.RuntimeMode} at startup.
 */
@Configuration
public class RuntimeConfiguration {

    private final TradingProperties properties;
    private final RuntimeModeHolder runtimeModeHolder;

    public RuntimeConfiguration(TradingProperties properties, RuntimeModeHolder runtimeModeHolder) {
        this.properties = properties;
        this.runtimeModeHolder = runtimeModeHolder;
    }

    @EventListener(ApplicationReadyEvent.class)
    void applyConfiguredMode() {
        if (properties.runtime() != null) {
            runtimeModeHolder.setMode(properties.runtime().mode());
        }
    }
}
