package com.tradej.broker.dhan.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.auth.DhanAuthenticationException;
import com.tradej.broker.dhan.exceptions.DhanBrokerException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-style tests for {@link DhanTokenRevalidator}. Uses fake
 * {@link DhanAuthClient} subclasses to keep the test deterministic and
 * fast — no real network calls.
 */
@Tag("unit")
class DhanTokenRevalidatorTest {

    private static DhanConnectionSettings settingsWithStateFile(Path stateFile) {
        return new DhanConnectionSettings(
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
                stateFile,
                10L,
                null,
                false
        );
    }

    @Test
    void updatesCachedExpiryWhenBrokerReportsLaterExpiry(@TempDir Path temp) throws Exception {
        Path stateFile = temp.resolve("token-state.json");
        Files.writeString(stateFile,
                "{\"accessToken\":\"abc\",\"expiryEpochMs\":1000,\"issuedAtEpochMs\":900,\"source\":\"STATIC\"}");
        DhanConnectionSettings settings = settingsWithStateFile(stateFile);
        DhanTokenStateStore store = new DhanTokenStateStore(stateFile, new ObjectMapper());
        ProfileAuthClient auth = new ProfileAuthClient(true, 5_000L, false);
        DhanTokenManager manager = new DhanTokenManager(
                settings, auth, new DhanTotpGenerator(), store,
                java.time.Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), java.time.ZoneId.of("UTC"))
        );

        DhanTokenRevalidator revalidator = new DhanTokenRevalidator(manager, auth, settings, 1L);
        boolean updated = revalidator.runOnce();
        assertTrue(updated, "revalidator should update cached expiry when broker reports a later value");
        assertEquals(5_000L, manager.currentSnapshot().expiryEpochMs());
        revalidator.close();
    }

    @Test
    void invalidatesOnConfirmedAuthenticationRejection(@TempDir Path temp) throws Exception {
        // DhanAuthenticationException represents a confirmed auth
        // rejection (401/403). The token is dead and no amount of
        // waiting will revive it — invalidate so the next ensureValid()
        // mints a fresh TOTP. This is the *only* error path that
        // invalidates; transient broker errors keep the cache.
        Path stateFile = temp.resolve("token-state.json");
        Files.writeString(stateFile,
                "{\"accessToken\":\"abc\",\"expiryEpochMs\":9999999999,\"issuedAtEpochMs\":900,\"source\":\"STATIC\"}");
        DhanConnectionSettings settings = settingsWithStateFile(stateFile);
        DhanTokenStateStore store = new DhanTokenStateStore(stateFile, new ObjectMapper());
        RejectingAuthClient auth = new RejectingAuthClient(new DhanAuthenticationException("simulated 401 unauthorized"));
        DhanTokenManager manager = new DhanTokenManager(
                settings, auth, new DhanTotpGenerator(), store,
                java.time.Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), java.time.ZoneId.of("UTC"))
        );

        DhanTokenRevalidator revalidator = new DhanTokenRevalidator(manager, auth, settings, 1L);
        boolean updated = revalidator.runOnce();
        assertFalse(updated, "revalidator should not update expiry on auth rejection");
        assertNull(manager.currentSnapshot(),
                "revalidator must invalidate cached state on DhanAuthenticationException");
        revalidator.close();
    }

    @Test
    void keepsCacheOnTransientBrokerError(@TempDir Path temp) throws Exception {
        // DhanBrokerException without auth-specific cause (e.g. 5xx,
        // network blip) is treated as transient — the cached token is
        // most likely still valid; only the profile endpoint failed.
        // Wiping the cache on a transient error was the historical
        // "5 minutes → 3 mints" bug.
        Path stateFile = temp.resolve("token-state.json");
        Files.writeString(stateFile,
                "{\"accessToken\":\"abc\",\"expiryEpochMs\":9999999999,\"issuedAtEpochMs\":900,\"source\":\"STATIC\"}");
        DhanConnectionSettings settings = settingsWithStateFile(stateFile);
        DhanTokenStateStore store = new DhanTokenStateStore(stateFile, new ObjectMapper());
        RejectingAuthClient auth = new RejectingAuthClient(new DhanBrokerException("simulated 503 service unavailable"));
        DhanTokenManager manager = new DhanTokenManager(
                settings, auth, new DhanTotpGenerator(), store,
                java.time.Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), java.time.ZoneId.of("UTC"))
        );

        DhanTokenRevalidator revalidator = new DhanTokenRevalidator(manager, auth, settings, 1L);
        boolean updated = revalidator.runOnce();
        assertFalse(updated, "revalidator should not update expiry on transient error");
        assertNotNull(manager.currentSnapshot(),
                "revalidator must NOT invalidate on a transient broker error — "
                        + "the token may still be valid; only the profile endpoint failed");
        revalidator.close();
    }

    @Test
    void keepsCacheWhenBrokerFlagsRefreshRecommended(@TempDir Path temp) throws Exception {
        // Previously: refresh-recommended flag from /v2/profile caused
        // immediate invalidation → next ensureValid() minted a fresh
        // token. With the throttle, we leave the cache intact and let
        // ensureValid() gate the mint on the 5-minute cooldown.
        Path stateFile = temp.resolve("token-state.json");
        Files.writeString(stateFile,
                "{\"accessToken\":\"abc\",\"expiryEpochMs\":9999999999,\"issuedAtEpochMs\":900,\"source\":\"STATIC\"}");
        DhanConnectionSettings settings = settingsWithStateFile(stateFile);
        DhanTokenStateStore store = new DhanTokenStateStore(stateFile, new ObjectMapper());
        ProfileAuthClient auth = new ProfileAuthClient(true, System.currentTimeMillis() - 1000L, true);
        DhanTokenManager manager = new DhanTokenManager(
                settings, auth, new DhanTotpGenerator(), store,
                java.time.Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), java.time.ZoneId.of("UTC"))
        );

        DhanTokenRevalidator revalidator = new DhanTokenRevalidator(manager, auth, settings, 1L);
        boolean updated = revalidator.runOnce();
        assertFalse(updated);
        assertNotNull(manager.currentSnapshot(),
                "revalidator must NOT invalidate on refresh-recommended — "
                        + "that's a normal signal, not an error; ensureValid() "
                        + "gates the mint on the 5-min throttle");
        revalidator.close();
    }

    @Test
    void noOpWhenCacheIsEmpty(@TempDir Path temp) {
        Path stateFile = temp.resolve("token-state.json");
        DhanConnectionSettings settings = settingsWithStateFile(stateFile);
        DhanTokenStateStore store = new DhanTokenStateStore(stateFile, new ObjectMapper());
        ProfileAuthClient auth = new ProfileAuthClient(true, 5_000L, false);
        DhanTokenManager manager = new DhanTokenManager(
                settings, auth, new DhanTotpGenerator(), store,
                java.time.Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), java.time.ZoneId.of("UTC"))
        );
        DhanTokenRevalidator revalidator = new DhanTokenRevalidator(manager, auth, settings, 1L);
        assertFalse(revalidator.runOnce());
        revalidator.close();
    }

    // ---- Test doubles ----

    private static final class ProfileAuthClient extends DhanAuthClient {
        private final boolean valid;
        private final long expiryEpochMs;
        private final boolean refreshRecommended;

        ProfileAuthClient(boolean valid, long expiryEpochMs, boolean refreshRecommended) {
            super();
            this.valid = valid;
            this.expiryEpochMs = expiryEpochMs;
            this.refreshRecommended = refreshRecommended;
        }

        @Override
        public DhanTokenInfo fetchProfile(String accessToken, long refreshBufferMillis) {
            return new DhanTokenInfo(valid, expiryEpochMs, refreshRecommended);
        }
    }

    private static final class RejectingAuthClient extends DhanAuthClient {
        private final RuntimeException toThrow;

        RejectingAuthClient(RuntimeException toThrow) {
            super();
            this.toThrow = toThrow;
        }

        @Override
        public DhanTokenInfo fetchProfile(String accessToken, long refreshBufferMillis) {
            throw toThrow;
        }
    }
}
