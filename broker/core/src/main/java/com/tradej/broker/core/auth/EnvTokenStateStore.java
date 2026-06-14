package com.tradej.broker.core.auth;

import com.tradej.broker.api.auth.TokenSource;
import com.tradej.broker.api.auth.TokenState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Environment variable-backed token state store for containerized deployments.
 *
 * <p>Reads token state from environment variables:
 * <ul>
 *   <li>{@code TRADEJ_TOKEN_ACCESS} — access token</li>
 *   <li>{@code TRADEJ_TOKEN_REFRESH} — refresh token (optional)</li>
 *   <li>{@code TRADEJ_TOKEN_EXPIRY_MS} — expiry epoch milliseconds</li>
 *   <li>{@code TRADEJ_TOKEN_ISSUED_MS} — issued-at epoch milliseconds</li>
 *   <li>{@code TRADEJ_TOKEN_SOURCE} — token source enum name (default: STATIC)</li>
 * </ul>
 *
 * <p>The save operation updates an in-memory cache only, since environment
 * variables are immutable at process level. For containerized deployments,
 * token refresh should be handled by a sidecar or init container that
 * updates the environment and restarts the process.
 *
 * <p>Configure via: {@code tradej.tokens.store-type=env}
 */
public final class EnvTokenStateStore implements TokenStateStore {

    private static final Logger log = LoggerFactory.getLogger(EnvTokenStateStore.class);

    static final String ENV_ACCESS = "TRADEJ_TOKEN_ACCESS";
    static final String ENV_REFRESH = "TRADEJ_TOKEN_REFRESH";
    static final String ENV_EXPIRY = "TRADEJ_TOKEN_EXPIRY_MS";
    static final String ENV_ISSUED = "TRADEJ_TOKEN_ISSUED_MS";
    static final String ENV_SOURCE = "TRADEJ_TOKEN_SOURCE";

    private volatile TokenState inMemoryState;

    @Override
    public TokenState load() {
        if (inMemoryState != null) {
            log.debug("Loaded token state from in-memory cache");
            return inMemoryState;
        }

        String accessToken = getEnv(ENV_ACCESS);
        if (accessToken == null || accessToken.isBlank()) {
            log.debug("No access token found in environment ({})", ENV_ACCESS);
            return null;
        }

        String refreshToken = getEnv(ENV_REFRESH);
        long expiryMs = parseLong(getEnv(ENV_EXPIRY), 0L);
        long issuedMs = parseLong(getEnv(ENV_ISSUED), 0L);
        TokenSource source = parseSource(getEnv(ENV_SOURCE));

        TokenState state = new TokenState(accessToken, refreshToken, expiryMs, issuedMs, source);
        log.info("Loaded token state from environment: expiry={}, source={}", expiryMs, source);
        return state;
    }

    @Override
    public void save(TokenState state) {
        if (state == null) {
            log.info("Clearing in-memory token state");
            this.inMemoryState = null;
            return;
        }
        this.inMemoryState = state;
        // HIGH-MED-2: surface the env-var persistence limitation prominently.
        // Process env vars are immutable. Calling save() here does NOT
        // persist across restarts. For a container deployment, configure
        // a sidecar/init container that updates TRADEJ_TOKEN_* and
        // restarts the JVM, or switch to JsonTokenStateStore mounted
        // to a writable volume.
        log.warn(
                "EnvTokenStateStore.save() keeps the token in process memory only. "
                        + "The new expiry ({} ms, source={}) will be LOST on process restart. "
                        + "Configure a sidecar/init container to rotate TRADEJ_TOKEN_EXPIRY_MS "
                        + "and TRADEJ_TOKEN_ISSUED_MS, or switch tradej.tokens.store-type=json "
                        + "with a writable volume.",
                state.expiryEpochMs(), state.source());
    }

    // Visible for testing
    String getEnv(String name) {
        return System.getenv(name);
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static TokenSource parseSource(String value) {
        if (value == null || value.isBlank()) return TokenSource.STATIC;
        try {
            return TokenSource.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return TokenSource.STATIC;
        }
    }
}
