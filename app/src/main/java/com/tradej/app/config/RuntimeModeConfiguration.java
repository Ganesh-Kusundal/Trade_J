package com.tradej.app.config;

import com.tradej.core.domain.runtime.RuntimeModeHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuntimeModeConfiguration {

    @Bean
    RuntimeModeHolder runtimeModeHolder() {
        return new RuntimeModeHolder();
    }
}
