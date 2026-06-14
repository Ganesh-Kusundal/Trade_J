package com.tradej.test.security;

import com.tradej.app.api.RuntimeModeController;
import com.tradej.app.security.JwtAuthFilter;
import com.tradej.app.security.JwtTokenService;
import com.tradej.app.security.RestAuthenticationEntryPoint;
import com.tradej.app.security.SecurityConfiguration;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring context for the RuntimeModeController security test.
 *
 * <p>Lives in a non-scanned package ({@code com.tradej.test.security}) so
 * the {@code @ComponentScan("com.tradej")} on {@code TradingApplication}
 * does not pick it up when the full application context is built for other
 * tests.
 */
@TestConfiguration
@Import({
        SecurityConfiguration.class,
        JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class,
        RuntimeModeController.class
})
public class RuntimeModeControllerSecurityTestContext {

    public static final String TEST_SECRET =
            "test-secret-with-at-least-32-characters-please-thanks";

    @Bean
    public RuntimeModeHolder runtimeModeHolder() {
        return new RuntimeModeHolder();
    }

    @Bean
    public JwtTokenService jwtTokenService() throws Exception {
        var ctor = JwtTokenService.class.getDeclaredConstructor(
                String.class, long.class, boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(TEST_SECRET, 3600L, false);
    }
}
