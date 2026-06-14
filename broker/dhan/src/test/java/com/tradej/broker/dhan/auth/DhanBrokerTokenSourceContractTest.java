package com.tradej.broker.dhan.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.api.auth.BrokerTokenSource;
import com.tradej.broker.api.auth.BrokerTokenSourceContractTest;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Concrete {@link BrokerTokenSource} contract test for the Dhan
 * implementation. Uses a static (pre-configured) token, a fake auth
 * client, and an isolated temp-dir state file so the test does not
 * touch the real broker.
 */
@Tag("unit")
class DhanBrokerTokenSourceContractTest extends BrokerTokenSourceContractTest {

    @TempDir
    Path tempDir;

    @Override
    protected BrokerTokenSource createSource() {
        DhanConnectionSettings settings = new DhanConnectionSettings(
                "client-1",
                "test-token",
                DhanApiEnvironment.SANDBOX,
                null,
                false,
                3,
                10,
                true,
                true,
                DhanAuthMode.STATIC,
                Path.of("config/dhan-pin.txt"),
                Path.of("config/dhan-totp-secret.txt"),
                tempDir.resolve("dhan-token-state.json"),
                10L,
                null,
                false
        );
        try {
            Files.writeString(tempDir.resolve("dhan-token-state.json"),
                    "{\"accessToken\":\"abc\",\"expiryEpochMs\":9999999999,\"issuedAtEpochMs\":900,\"source\":\"STATIC\"}");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        DhanTokenStateStore store = new DhanTokenStateStore(
                tempDir.resolve("dhan-token-state.json"), new ObjectMapper());
        StaticAuthClient auth = new StaticAuthClient();
        return new DhanTokenManager(
                settings, auth, new DhanTotpGenerator(), store,
                Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneId.of("UTC"))
        );
    }

    @Override
    protected boolean supportsListenerRegistry() {
        return true;
    }

    /** Static auth client: never fails; reports the static token as valid. */
    private static final class StaticAuthClient extends DhanAuthClient {
        @Override
        public DhanTokenInfo fetchProfile(String accessToken, long refreshBufferMillis) {
            return new DhanTokenInfo(true, Long.MAX_VALUE / 2, false);
        }
    }
}
