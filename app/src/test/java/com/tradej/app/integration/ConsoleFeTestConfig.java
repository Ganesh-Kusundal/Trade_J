package com.tradej.app.integration;

import com.tradej.app.admin.DashboardRedirectController;
import com.tradej.app.config.WebConfiguration.RateLimitFilter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Serves static console assets and redirect routes for FE+BE smoke tests.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({DashboardRedirectController.class, RateLimitFilter.class})
class ConsoleFeTestConfig {
}
