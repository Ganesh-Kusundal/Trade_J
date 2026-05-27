package com.tradej.app.integration;

import com.tradej.broker.dhan.auth.DhanTokenInfo;
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanTokenLifecycleIntegrationTest {
    @Test
    void reusesValidTokenWithoutRotatingAndReportsProfileValidity() throws Exception {
        DhanConnectionSettings baseSettings = LiveDhanTestSupport.connectionSettingsOrSkip();
        Path tokenStateFile = Files.createTempFile("dhan-token-state", ".json");
        Files.deleteIfExists(tokenStateFile);
        DhanConnectionSettings managedSettings = DhanConnectionSettings.withDefaults(
                baseSettings.clientId(),
                baseSettings.accessToken(),
                DhanAuthMode.TOTP_GENERATED,
                baseSettings.pinFile(),
                baseSettings.totpSecretFile(),
                tokenStateFile,
                baseSettings.refreshBufferMinutes()
        );

        DhanTokenManager manager = new DhanTokenManager(managedSettings);

        String first = manager.getAccessToken();
        String second = manager.getAccessToken();
        DhanTokenInfo tokenInfo = manager.getTokenInfo();

        assertEquals(first, second, "A still-valid Dhan token should be reused instead of regenerated.");
        assertTrue(tokenInfo.valid(), "Token manager should confirm that the active Dhan token is valid.");
        assertTrue(Files.exists(tokenStateFile), "Managed auth mode should persist the adopted token state locally.");
    }
}
