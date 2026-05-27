package com.tradej.app.config;

import com.tradej.core.domain.port.NetPositionProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@link org.springframework.scheduling.annotation.Scheduled @Scheduled}
 * support for periodic tasks such as order reconciliation.
 *
 * <p>The scheduling infrastructure (thread pool) is auto-configured by
 * Spring Boot's {@code @EnableScheduling} using a single-threaded
 * executor by default. For production use with multiple scheduled tasks,
 * consider customizing the executor via
 * {@link org.springframework.scheduling.annotation.SchedulingConfigurer}.
 *
 * <p>Registers a default {@link NetPositionProvider} that returns an empty map
 * (no expected positions). A real implementation (e.g. from the strategy engine)
 * should register its own {@code @Bean} to override this default.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {

    @Bean
    @ConditionalOnMissingBean(NetPositionProvider.class)
    NetPositionProvider netPositionProvider() {
        return NetPositionProvider.empty();
    }
}
