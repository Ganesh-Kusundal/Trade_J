package com.tradej.broker.upstox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot configuration properties for the Upstox broker adapter.
 *
 * <p>Binds to the {@code trade.upstox} namespace.
 */
@ConfigurationProperties(prefix = "trade.upstox")
public record UpstoxConnectionProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String accessToken,
        String refreshToken,
        String analyticsToken,
        String extendedToken,
        boolean analyticsOnly,
        boolean sandbox,
        int redirectServerPort,
        long refreshBufferMs,
        long tokenExpiryBufferMs
) {
    public UpstoxConnectionProperties {
        if (redirectServerPort == 0) redirectServerPort = 18080;
        if (refreshBufferMs == 0) refreshBufferMs = 1_800_000L;
        if (tokenExpiryBufferMs == 0) tokenExpiryBufferMs = 600_000L;
    }

    public UpstoxConnectionSettings toSettings() {
        return new UpstoxConnectionSettings(
                clientId,
                clientSecret,
                redirectUri,
                accessToken,
                refreshToken,
                analyticsToken,
                extendedToken,
                analyticsOnly,
                sandbox,
                redirectServerPort,
                refreshBufferMs,
                tokenExpiryBufferMs
        );
    }
}
