package com.tradej.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@link org.springframework.scheduling.annotation.Scheduled @Scheduled}
 * support for periodic tasks such as order reconciliation.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
