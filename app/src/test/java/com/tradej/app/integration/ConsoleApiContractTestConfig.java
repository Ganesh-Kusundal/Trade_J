package com.tradej.app.integration;

import com.tradej.app.admin.AdminController;
import com.tradej.app.api.PipelineController;
import com.tradej.app.api.StudioController;
import com.tradej.app.config.RateLimitFilter;
import com.tradej.app.config.ScanProperties;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot wiring for console REST contract tests.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({AdminController.class, StudioController.class, PipelineController.class, RateLimitFilter.class})
class ConsoleApiContractTestConfig {

    @Bean
    ScanProperties scanProperties() {
        return new ScanProperties(true, "institutional-baseline", java.util.List.of());
    }

}
