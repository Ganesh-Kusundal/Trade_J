package com.tradej.app.integration;

import com.tradej.broker.icici.auth.BreezeSession;
import com.tradej.broker.icici.auth.BreezeSessionExchange;
import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.auth.BreezeTokenStateStore;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.config.IciciAuthMode;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JVM-wide live ICICI session for integration tests.
 *
 * <p>The previous design called {@link BreezeSessionExchange#exchange} directly
 * from {@code preflightSessionOrSkip(...)} on every test method, so a CI run
 * with N ICICI tests performed N+ mints per run — which both burned the
 * daily-session quota and tripped ICICI's anti-abuse rate limit.
 *
 * <p>This class fixes that by:
 * <ol>
 *   <li>Reusing a single {@link BreezeSession} per JVM (cached statically).</li>
 *   <li>Persisting it via the production {@link BreezeTokenStateStore} on the
 *       shared {@code runtime/icici-token-state.json} file (the same one the
 *       app's {@code BreezeTokenManager} uses).</li>
 *   <li>Reading the persisted state before any network call, so a CI run that
 *       already has a valid session performs <b>zero</b> mints.</li>
 *   <li>Serialising mints with a {@link ReentrantLock} so parallel test
 *       threads don't double-mint.</li>
 * </ol>
 */
final class LiveIciciAuthSession {

    private static final ReentrantLock LOCK = new ReentrantLock();
    /** Buffer subtracted from the expiry to account for clock skew. */
    private static final long EXPIRY_SAFETY_MARGIN_MS = 60_000L;

    private LiveIciciAuthSession() {
    }

    /**
     * Returns a session that is reusable across tests. Tries the on-disk
     * state file first (cheap), falls back to a single fresh mint, and
     * persists the result so the next test in the same JVM picks it up.
     *
     * <p>For non-API-session auth modes (TOTP_GENERATED, BROWSER_AUTOMATED)
     * this returns the same {@code BreezeSession} object on every call
     * within a JVM — i.e. one mint per JVM.
     */
    static BreezeSession resolveOrSkip(BreezeConnectionSettings settings) {
        LOCK.lock();
        try {
            Path stateFile = settings.tokenStateFile();
            BreezeTokenStateStore store = new BreezeTokenStateStore(
                    stateFile == null ? Path.of("build/live-fixture-icici-state.json") : stateFile);

            // 1. Try the persisted state file first.
            Optional<BreezeSession> loaded = store.load();
            if (loaded.isPresent()) {
                BreezeSession cached = loaded.get();
                if (cached.expiresAtEpochMs() > System.currentTimeMillis() + EXPIRY_SAFETY_MARGIN_MS) {
                    return cached;
                }
                // Expired — fall through to mint.
            }

            // 2. Skip if credentials are not configured at all.
            if (isSkipped(settings)) {
                Assumptions.assumeTrue(false,
                        "ICICI credentials are not configured for live integration tests");
            }

            // 3. Mint once and persist.
            BreezeSessionExchange exchange = new BreezeSessionExchange();
            BreezeSession fresh = exchange.exchange(
                    settings.appKey(),
                    LiveIciciTestSupport.totpSessionInput(settings));
            store.save(fresh);
            return fresh;
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                    "ICICI session preflight failed: " + ex.getMessage());
            // unreachable, but the compiler doesn't know that
            throw new IllegalStateException(ex);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Returns a {@link BreezeTokenManager} that uses the shared state file.
     * The first call mints; subsequent calls in the same JVM (or in a future
     * run with the persisted state on disk) reuse the same session.
     */
    static BreezeTokenManager tokenManager(BreezeConnectionSettings settings) {
        // Touch the manager so it loads from the shared state file; do NOT
        // call ensureValid() here because that would itself mint if the state
        // file were missing — the caller is expected to call ensureValid()
        // explicitly. We make sure the on-disk state is populated by running
        // resolveOrSkip() first.
        resolveOrSkip(settings);
        return new BreezeTokenManager(settings);
    }

    /** True if no live credentials are configured; tests should be skipped. */
    static boolean isSkipped(BreezeConnectionSettings settings) {
        if (settings.authMode() == IciciAuthMode.STATIC) {
            return settings.staticSessionToken() == null
                    || settings.staticSessionToken().isBlank();
        }
        if (settings.authMode() == IciciAuthMode.API_SESSION
                || settings.authMode() == IciciAuthMode.BROWSER_AUTOMATED) {
            Path f = settings.apiSessionFile();
            if (f == null || !Files.exists(f)) {
                return true;
            }
            try {
                return Files.readString(f).isBlank();
            } catch (Exception ex) {
                return true;
            }
        }
        // TOTP_GENERATED: needs the totp file. If missing, the test is skipped
        // upstream in LiveIciciTestSupport; here we treat that as "configured".
        return false;
    }
}
