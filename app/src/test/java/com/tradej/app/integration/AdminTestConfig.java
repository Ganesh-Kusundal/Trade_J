package com.tradej.app.integration;

import com.tradej.app.admin.AdminController;
import com.tradej.app.config.RateLimitFilter;
import com.tradej.app.service.AdminApplicationService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot configuration for {@link AdminController} integration tests.
 * <p>
 * Loads only the controller, its application service, and rate-limit filter — all other
 * dependencies are mocked via {@link org.springframework.test.context.bean.override.mockito.MockitoBean}
 * in the test classes that extend {@link AdminTestBase}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({AdminController.class, AdminApplicationService.class, RateLimitFilter.class})
class AdminTestConfig {
}
