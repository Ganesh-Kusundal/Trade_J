package com.tradej.broker.icici.config;

import java.nio.file.Path;

public record BreezeConnectionSettings(
        String appKey,
        String secretKey,
        String staticSessionToken,
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
    public static BreezeConnectionSettings withDefaults(
            String appKey,
            String secretKey,
            String staticSessionToken,
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
        return new BreezeConnectionSettings(
                appKey,
                secretKey,
                staticSessionToken,
                authMode,
                totpSecretFile,
                usernameFile,
                passwordFile,
                apiSessionFile,
                tokenStateFile,
                ordersEnabled,
                refreshBufferMinutes,
                loginRedirectPort,
                loginRedirectPath,
                browserHeadless,
                browserLoginTimeoutSeconds
        );
    }
}
