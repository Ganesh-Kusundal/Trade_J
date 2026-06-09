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
    public BreezeConnectionSettings {
        if (appKey == null || appKey.isBlank()) {
            throw new IllegalArgumentException("appKey must not be blank");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("secretKey must not be blank");
        }
        if (authMode == null) {
            throw new IllegalArgumentException("authMode must not be null");
        }
        if (authMode == IciciAuthMode.TOTP_GENERATED && (totpSecretFile == null || usernameFile == null || passwordFile == null)) {
            throw new IllegalArgumentException("totpSecretFile, usernameFile, and passwordFile are required when authMode=TOTP_GENERATED");
        }
        if (authMode == IciciAuthMode.BROWSER_AUTOMATED && (usernameFile == null || passwordFile == null)) {
            throw new IllegalArgumentException("usernameFile and passwordFile are required when authMode=BROWSER_AUTOMATED");
        }
    }
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
