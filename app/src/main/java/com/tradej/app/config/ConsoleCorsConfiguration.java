package com.tradej.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Optional REST CORS for split hosting (SPA on a different origin than the API).
 * WebSocket CORS is configured separately on the gateway endpoint.
 * <p>
 * Enable with {@code tradej.console.cors.enabled=true} and optional
 * {@code tradej.console.cors.allowed-origins} (comma-separated).
 */
@Configuration
@ConditionalOnProperty(name = "tradej.console.cors.enabled", havingValue = "true")
public class ConsoleCorsConfiguration {

    @Bean
    CorsFilter consoleCorsFilter(
            @Value("${tradej.console.cors.allowed-origins:*}") String allowedOrigins
    ) {
        CorsConfiguration config = new CorsConfiguration();
        if ("*".equals(allowedOrigins.trim())) {
            config.addAllowedOriginPattern("*");
        } else {
            List<String> origins = Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            config.setAllowedOrigins(origins);
        }
        config.addAllowedMethod(CorsConfiguration.ALL);
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/admin/**", config);
        source.registerCorsConfiguration("/actuator/**", config);
        return new CorsFilter(source);
    }
}
