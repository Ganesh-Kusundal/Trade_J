package com.tradej.app.integration;

import com.tradej.broker.dhan.auth.DhanAuthRejectedException;
import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JVM-wide live Dhan token session for integration tests.
 *
 * <p>Enforces Dhan's practical limit of one TOTP mint per ~2 minutes and reuses a single
 * access token across all tests in the same Gradle JVM run.
 *
 * <p><b>Parallel safety constraint:</b> This class uses shared static state
 * ({@code cachedAccessToken}, {@code cachedClientId}, {@code lastMintEpochMs}) protected
 * by a {@link ReentrantLock}. While thread-safe, the singleton design means <b>tests using
 * this class must NOT run concurrently</b> — Dhan enforces a TOTP mint cooldown of ~2 minutes,
 * and parallel token minting across test threads would hit rate limits.
 *
 * <p>To enforce sequential execution, annotate test classes that depend on this session with
 * {@code @Isolated} (from {@code org.junit.jupiter.api.parallel.Isolated}).
 */
final class LiveDhanAuthSession {

    /** Observed Dhan cooldown: "Token can be generated once every 2 minutes." */
    static final long MINT_COOLDOWN_MS = 120_000L;

    private static final ReentrantLock LOCK = new ReentrantLock();

    private static volatile String cachedClientId;
    private static volatile String cachedAccessToken;
    private static volatile long lastMintEpochMs;

    private LiveDhanAuthSession() {
    }

    static String resolve(DhanConnectionSettings settings, boolean forceRefresh) {
        if (settings.authMode() == DhanAuthMode.STATIC) {
            return settings.accessToken();
        }

        LOCK.lock();
        try {
            String fundLimitUrl = settings.restBaseUrl() + "/fundlimit";

            if (!forceRefresh
                    && cachedAccessToken != null
                    && settings.clientId().equals(cachedClientId)
                    && LiveDhanTestSupport.preflightAuth(settings.clientId(), cachedAccessToken, fundLimitUrl)) {
                return cachedAccessToken;
            }

            DhanTokenManager manager = new DhanTokenManager(settings);
            String token;
            try {
                token = manager.getAccessToken();
            } catch (DhanAuthRejectedException ex) {
                if (ex.rateLimited() && cachedAccessToken != null
                        && LiveDhanTestSupport.preflightAuth(settings.clientId(), cachedAccessToken, fundLimitUrl)) {
                    return cachedAccessToken;
                }
                throw ex;
            }

            if (LiveDhanTestSupport.preflightAuth(settings.clientId(), token, fundLimitUrl)) {
                cache(settings.clientId(), token);
                return token;
            }

            if (!forceRefresh) {
                throw new IllegalStateException(
                        "Live Dhan token failed /fundlimit preflight. Refresh credentials with "
                                + "scripts/refresh-dhan-token.sh (wait 2 minutes between TOTP mints), "
                                + "or set DHAN_FORCE_TOKEN_REFRESH=true to force regeneration.");
            }

            enforceMintCooldown();
            lastMintEpochMs = System.currentTimeMillis();
            try {
                Files.deleteIfExists(settings.tokenStateFile());
            } catch (IOException ex) {
                throw new IllegalStateException(
                        "Failed to clear stale Dhan token state at " + settings.tokenStateFile(), ex);
            }

            try {
                DhanTokenManager refreshed = new DhanTokenManager(settings);
                token = refreshed.getAccessToken();
            } catch (DhanAuthRejectedException ex) {
                if (ex.rateLimited() && cachedAccessToken != null
                        && LiveDhanTestSupport.preflightAuth(settings.clientId(), cachedAccessToken, fundLimitUrl)) {
                    return cachedAccessToken;
                }
                throw ex;
            }

            if (!LiveDhanTestSupport.preflightAuth(settings.clientId(), token, fundLimitUrl)) {
                throw new IllegalStateException(
                        "Live Dhan token still failed /fundlimit preflight after forced refresh.");
            }
            cache(settings.clientId(), token);
            return token;
        } finally {
            LOCK.unlock();
        }
    }

    private static void cache(String clientId, String token) {
        cachedClientId = clientId;
        cachedAccessToken = token;
    }

    private static void enforceMintCooldown() {
        long elapsed = System.currentTimeMillis() - lastMintEpochMs;
        if (lastMintEpochMs > 0L && elapsed < MINT_COOLDOWN_MS) {
            long waitSec = (MINT_COOLDOWN_MS - elapsed + 999L) / 1000L;
            throw new IllegalStateException(
                    "Dhan TOTP mint cooldown active — wait " + waitSec + "s before forcing another token generation.");
        }
    }
}
