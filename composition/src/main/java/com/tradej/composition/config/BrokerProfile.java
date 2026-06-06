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
