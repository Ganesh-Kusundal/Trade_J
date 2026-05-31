package com.tradej.app.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.dhan.auth.DhanAuthClient;
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.auth.DhanTokenState;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-auth-drill")
class DhanTokenForcedGenerationIntegrationTest {
  private static final String INVALID_BOOTSTRAP = "INVALID_BOOTSTRAP_TOKEN_FOR_TOTP_DRILL";

  @Test
  void generatesFreshTokenWhenBootstrapInvalidAndStateFileEmpty() throws Exception {
    DhanConnectionSettings base = LiveDhanTestSupport.connectionSettingsOrSkip();
    Assumptions.assumeTrue(base.authMode() == DhanAuthMode.TOTP_GENERATED,
        "Forced TOTP generation drill requires dhan.authMode=TOTP_GENERATED.");
    assumeTotpCredentialsPresent(base);

    Path tokenStateFile = Files.createTempFile("dhan-forced-totp-state", ".json");
    Files.deleteIfExists(tokenStateFile);

    DhanConnectionSettings settings = new DhanConnectionSettings(
        base.clientId(),
        INVALID_BOOTSTRAP,
        base.environment(),
        base.restBaseUrl(),
        base.loggingEnabled(),
        base.rateLimitRetries(),
        base.maxReconnectAttempts(),
        base.autoReconnectEnabled(),
        base.autoResubscribeEnabled(),
        DhanAuthMode.TOTP_GENERATED,
        base.pinFile(),
        base.totpSecretFile(),
        tokenStateFile,
        base.refreshBufferMinutes()
    );

    DhanTokenManager manager = new DhanTokenManager(settings);
    String token = manager.getAccessToken();

    assertTrue(Files.exists(tokenStateFile), "Token state should be written to the configured override file.");
    DhanTokenState persisted = new ObjectMapper().readValue(Files.readString(tokenStateFile), DhanTokenState.class);
    assertEquals("TOTP_GENERATED", persisted.source());
    assertNotEquals(INVALID_BOOTSTRAP, token);
    assertTrue(new DhanAuthClient().fetchProfile(token, settings.refreshBufferMillis()).valid());
  }

  private static void assumeTotpCredentialsPresent(DhanConnectionSettings settings) {
    Assumptions.assumeTrue(Files.exists(settings.pinFile()), "Missing pin file at " + settings.pinFile());
    Assumptions.assumeTrue(Files.exists(settings.totpSecretFile()), "Missing TOTP secret file at " + settings.totpSecretFile());
  }
}
