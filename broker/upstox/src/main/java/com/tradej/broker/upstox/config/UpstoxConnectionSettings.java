package com.tradej.broker.upstox.config;

/**
 * Immutable configuration for the Upstox broker connection.
 */
public record UpstoxConnectionSettings(
        String clientId,
        String clientSecret,
        String redirectUri,
        String accessToken,
        String refreshToken,
        String analyticsToken,
        String extendedToken,
        boolean analyticsOnly,
        boolean isSandbox,
        int redirectServerPort,
        long refreshBufferMs,
        long tokenExpiryBufferMs
) {
    public UpstoxConnectionSettings {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("clientSecret must not be blank");
        }
        if (analyticsOnly && (analyticsToken == null || analyticsToken.isBlank())) {
            throw new IllegalArgumentException("analyticsToken is required when analyticsOnly=true");
        }
    }

    /** Convenience factory for sandbox settings with OAuth. */
    public static UpstoxConnectionSettings sandbox(String clientId, String clientSecret, String redirectUri) {
        return new UpstoxConnectionSettings(
                clientId, clientSecret, redirectUri,
                null, null, null, null, false, true, 18080,
                1_800_000L, 600_000L
        );
    }

    /** Convenience factory for live settings with OAuth. */
    public static UpstoxConnectionSettings live(String clientId, String clientSecret, String redirectUri) {
        return new UpstoxConnectionSettings(
                clientId, clientSecret, redirectUri,
                null, null, null, null, false, false, 18080,
                1_800_000L, 600_000L
        );
    }
}
