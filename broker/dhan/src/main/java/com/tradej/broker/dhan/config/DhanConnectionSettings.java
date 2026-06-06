package com.tradej.broker.dhan.config;

import java.nio.file.Path;
import java.util.Objects;

public record DhanConnectionSettings(
        String clientId,
        String accessToken,
        DhanApiEnvironment environment,
        String restBaseUrl,
        boolean loggingEnabled,
        int rateLimitRetries,
        int maxReconnectAttempts,
        boolean autoReconnectEnabled,
        boolean autoResubscribeEnabled,
        DhanAuthMode authMode,
        Path pinFile,
        Path totpSecretFile,
        Path tokenStateFile,
        long refreshBufferMinutes,
        String depthWsUrl,
        boolean killSwitchTestEnabled
) {
    private static final String LIVE_BASE_URL = "https://api.dhan.co/v2";
    private static final String SANDBOX_BASE_URL = "https://sandbox.dhan.co/v2";
    private static final String DEPTH_WS_URL = "wss://depth-api-feed.dhan.co/twentydepth";

    public DhanConnectionSettings {
        environment = Objects.requireNonNullElse(environment, DhanApiEnvironment.LIVE);
        restBaseUrl = normalizeBaseUrl(restBaseUrl, environment);
        depthWsUrl = Objects.requireNonNullElse(depthWsUrl, defaultDepthWsUrl(environment));
        killSwitchTestEnabled = Objects.requireNonNullElse(killSwitchTestEnabled, false);
    }

    /**
     * Create settings with sensible defaults for most fields.
     * Equivalent to Python's BrokerConfig.from_env() — sets standard
     * retry, reconnect, and logging behaviour so callers only need
     * to supply the two mandatory credentials.
     *
     * @param clientId     Dhan client ID
     * @param accessToken  Dhan access token
     */
    public static DhanConnectionSettings withDefaults(String clientId, String accessToken) {
        return liveWithDefaults(clientId, accessToken);
    }

    public static DhanConnectionSettings liveWithDefaults(String clientId, String accessToken) {
        return withDefaults(
                clientId,
                accessToken,
                DhanApiEnvironment.LIVE,
                null,
                DhanAuthMode.STATIC,
                Path.of("config/dhan-pin.txt"),
                Path.of("config/dhan-totp-secret.txt"),
                Path.of("runtime/dhan-token-state.json"),
                10L
        );
    }

    public static DhanConnectionSettings sandboxWithDefaults(String clientId, String accessToken) {
        return withDefaults(
                clientId,
                accessToken,
                DhanApiEnvironment.SANDBOX,
                null,
                DhanAuthMode.STATIC,
                Path.of("config/dhan-pin.txt"),
                Path.of("config/dhan-totp-secret.txt"),
                Path.of("runtime/dhan-token-state.json"),
                10L
        );
    }

    public static DhanConnectionSettings withDefaults(
            String clientId,
            String accessToken,
            DhanAuthMode authMode,
            Path pinFile,
            Path totpSecretFile,
            Path tokenStateFile,
            long refreshBufferMinutes
    ) {
        return withDefaults(
                clientId,
                accessToken,
                DhanApiEnvironment.LIVE,
                null,
                authMode,
                pinFile,
                totpSecretFile,
                tokenStateFile,
                refreshBufferMinutes
        );
    }

    public static DhanConnectionSettings withDefaults(
            String clientId,
            String accessToken,
            DhanApiEnvironment environment,
            String restBaseUrl,
            DhanAuthMode authMode,
            Path pinFile,
            Path totpSecretFile,
            Path tokenStateFile,
            long refreshBufferMinutes
    ) {
        return new DhanConnectionSettings(
                clientId,
                accessToken,
                Objects.requireNonNullElse(environment, DhanApiEnvironment.LIVE),
                restBaseUrl,
                false,
                3,
                10,
                true,
                true,
                Objects.requireNonNullElse(authMode, DhanAuthMode.STATIC),
                pinFile,
                totpSecretFile,
                tokenStateFile,
                refreshBufferMinutes,
                null,
                false
        );
    }

    public static String defaultBaseUrl(DhanApiEnvironment environment) {
        return environment == DhanApiEnvironment.SANDBOX ? SANDBOX_BASE_URL : LIVE_BASE_URL;
    }

    public static String defaultDepthWsUrl(@SuppressWarnings("unused") DhanApiEnvironment environment) {
        return DEPTH_WS_URL;
    }

    public long refreshBufferMillis() {
        return Math.max(1L, refreshBufferMinutes) * 60_000L;
    }

    public boolean hasConfiguredAccessToken() {
        return accessToken != null && !accessToken.isBlank();
    }

    public boolean isSandbox() {
        return environment == DhanApiEnvironment.SANDBOX;
    }

    public DhanConnectionSettings withTokenStateFile(Path path) {
        return new DhanConnectionSettings(
                clientId,
                accessToken,
                environment,
                restBaseUrl,
                loggingEnabled,
                rateLimitRetries,
                maxReconnectAttempts,
                autoReconnectEnabled,
                autoResubscribeEnabled,
                authMode,
                pinFile,
                totpSecretFile,
                path,
                refreshBufferMinutes,
                depthWsUrl,
                killSwitchTestEnabled
        );
    }

    public DhanConnectionSettings withDepthWsUrl(String url) {
        return new DhanConnectionSettings(
                clientId,
                accessToken,
                environment,
                restBaseUrl,
                loggingEnabled,
                rateLimitRetries,
                maxReconnectAttempts,
                autoReconnectEnabled,
                autoResubscribeEnabled,
                authMode,
                pinFile,
                totpSecretFile,
                tokenStateFile,
                refreshBufferMinutes,
                url,
                killSwitchTestEnabled
        );
    }

    public DhanConnectionSettings withKillSwitchTestEnabled(boolean enabled) {
        return new DhanConnectionSettings(
                clientId,
                accessToken,
                environment,
                restBaseUrl,
                loggingEnabled,
                rateLimitRetries,
                maxReconnectAttempts,
                autoReconnectEnabled,
                autoResubscribeEnabled,
                authMode,
                pinFile,
                totpSecretFile,
                tokenStateFile,
                refreshBufferMinutes,
                depthWsUrl,
                enabled
        );
    }

    public String depthWsUrl() {
        return depthWsUrl;
    }

    public boolean killSwitchTestEnabled() {
        return killSwitchTestEnabled;
    }

    private static String normalizeBaseUrl(String baseUrl, DhanApiEnvironment environment) {
        String resolved = (baseUrl == null || baseUrl.isBlank()) ? defaultBaseUrl(environment) : baseUrl.trim();
        return resolved.endsWith("/") ? resolved.substring(0, resolved.length() - 1) : resolved;
    }
}
