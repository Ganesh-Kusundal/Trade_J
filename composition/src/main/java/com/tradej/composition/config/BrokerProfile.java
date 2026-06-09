package com.tradej.composition.config;

import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.icici.config.IciciAuthMode;

import java.nio.file.Path;

public record BrokerProfile(
        BrokerType brokerType,
        DhanConfig dhan,
        UpstoxConfig upstox,
        IciciConfig icici
) {
    /**
     * Validate broker profile before startup.
     * Throws IllegalStateException if required configuration is missing or invalid.
     */
    public void validate() {
        switch (brokerType) {
            case DHAN -> {
                if (dhan == null) {
                    throw new IllegalStateException("Dhan configuration is required for broker type DHAN");
                }
                if (dhan.clientId() == null || dhan.clientId().isBlank()) {
                    throw new IllegalStateException("Dhan clientId must not be blank");
                }
                if (dhan.authMode() == null) {
                    throw new IllegalStateException("Dhan authMode must not be null");
                }
                if (dhan.authMode() == DhanAuthMode.STATIC && (dhan.accessToken() == null || dhan.accessToken().isBlank())) {
                    throw new IllegalStateException("Dhan accessToken is required when authMode=STATIC");
                }
                if (dhan.authMode() == DhanAuthMode.TOTP_GENERATED && (dhan.pinFile() == null || dhan.totpSecretFile() == null)) {
                    throw new IllegalStateException("Dhan pinFile and totpSecretFile are required when authMode=TOTP_GENERATED");
                }
            }
            case UPSTOX -> {
                if (upstox == null) {
                    throw new IllegalStateException("Upstox configuration is required for broker type UPSTOX");
                }
                if (upstox.clientId() == null || upstox.clientId().isBlank()) {
                    throw new IllegalStateException("Upstox clientId must not be blank");
                }
                if (upstox.clientSecret() == null || upstox.clientSecret().isBlank()) {
                    throw new IllegalStateException("Upstox clientSecret must not be blank");
                }
                if (upstox.analyticsOnly() && (upstox.analyticsToken() == null || upstox.analyticsToken().isBlank())) {
                    throw new IllegalStateException("Upstox analyticsToken is required when analyticsOnly=true");
                }
            }
            case ICICI -> {
                if (icici == null) {
                    throw new IllegalStateException("ICICI configuration is required for broker type ICICI");
                }
                if (icici.appKey() == null || icici.appKey().isBlank()) {
                    throw new IllegalStateException("ICICI appKey must not be blank");
                }
                if (icici.secretKey() == null || icici.secretKey().isBlank()) {
                    throw new IllegalStateException("ICICI secretKey must not be blank");
                }
                if (icici.authMode() == null) {
                    throw new IllegalStateException("ICICI authMode must not be null");
                }
                if (icici.authMode() == IciciAuthMode.TOTP_GENERATED && (icici.totpSecretFile() == null || icici.usernameFile() == null || icici.passwordFile() == null)) {
                    throw new IllegalStateException("ICICI totpSecretFile, usernameFile, and passwordFile are required when authMode=TOTP_GENERATED");
                }
                if (icici.authMode() == IciciAuthMode.BROWSER_AUTOMATED && (icici.usernameFile() == null || icici.passwordFile() == null)) {
                    throw new IllegalStateException("ICICI usernameFile and passwordFile are required when authMode=BROWSER_AUTOMATED");
                }
            }
            case GATEWAY -> {
                // Gateway mode — no broker-specific validation needed
            }
        }
    }
    public enum BrokerType {
        DHAN, UPSTOX, ICICI, GATEWAY
    }

    public record DhanConfig(
            String clientId,
            String accessToken,
            DhanApiEnvironment environment,
            String restBaseUrl,
            DhanAuthMode authMode,
            Path pinFile,
            Path totpSecretFile,
            Path tokenStateFile,
            long refreshBufferMinutes,
            boolean autoDownload,
            String instrumentCacheDirectory
    ) {
    }

    public record UpstoxConfig(
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
    }

    public record IciciConfig(
            String appKey,
            String secretKey,
            String sessionToken,
            IciciAuthMode authMode,
            Path totpSecretFile,
            Path usernameFile,
            Path passwordFile,
            Path apiSessionFile,
            Path tokenStateFile,
            boolean ordersEnabled,
            long refreshBufferMinutes,
            int loginRedirectPort,
            String loginRedirectPath,
            boolean browserHeadless,
            long browserLoginTimeoutSeconds
    ) {
    }
}
