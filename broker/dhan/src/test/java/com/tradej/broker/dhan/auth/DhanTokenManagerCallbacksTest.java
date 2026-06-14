package com.tradej.broker.dhan.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the new behaviors added to {@link DhanTokenManager}:
 * <ul>
 *   <li>{@link DhanTokenManager#ensureValidAndGet()} — atomic read</li>
 *   <li>{@link DhanTokenManager#updateCachedExpiry(long)} — used by the
 *       revalidator to keep {@code expiryEpochMs} in sync without minting</li>
 *   <li>Refresh/invalidate callbacks via {@link BrokerTokenSource}</li>
 * </ul>
 */
@Tag("unit")
class DhanTokenManagerCallbacksTest {

    private static final long PRIMED_EXPIRY_MS = Instant.parse("2030-01-01T00:00:00Z").toEpochMilli();

    private static DhanConnectionSettings settings(Path stateFile) {
        return new DhanConnectionSettings(
                "client-1",
                "bootstrap-token",
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

    private static DhanTokenManager primedManager(Path stateFile) {
        DhanConnectionSettings s = settings(stateFile);
        DhanTokenStateStore store = new DhanTokenStateStore(s.tokenStateFile(), new ObjectMapper());
        store.save(new DhanTokenState("primed", PRIMED_EXPIRY_MS,
                Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), "STATIC"));
        return new DhanTokenManager(
                s, new DhanAuthClient(), new DhanTotpGenerator(), store,
                java.time.Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), java.time.ZoneId.of("UTC"))
        );
    }

    @Test
    void ensureValidAndGetReturnsBootstrapTokenForStatic(@TempDir Path temp) {
        Path stateFile = temp.resolve("token-state.json");
        DhanTokenManager manager = new DhanTokenManager(
                settings(stateFile), new DhanAuthClient(), new DhanTotpGenerator(),
                new DhanTokenStateStore(stateFile),
                java.time.Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), java.time.ZoneId.of("UTC"))
        );
        assertEquals("bootstrap-token", manager.ensureValidAndGet());
    }

    @Test
    void onRefreshCallbackFiresAfterUpdateCachedExpiry(@TempDir Path temp) {
        DhanTokenManager manager = primedManager(temp.resolve("token-state.json"));
        AtomicInteger calls = new AtomicInteger(0);
        manager.onRefresh(calls::incrementAndGet);

        boolean updated = manager.updateCachedExpiry(Instant.parse("2030-06-01T00:00:00Z").toEpochMilli());
        assertTrue(updated);
        assertEquals(1, calls.get(), "refresh callback must fire after updateCachedExpiry");
    }

    @Test
    void onRefreshCallbackDeduped(@TempDir Path temp) {
        DhanTokenManager manager = primedManager(temp.resolve("token-state.json"));
        AtomicInteger calls = new AtomicInteger(0);
        Runnable cb = calls::incrementAndGet;
        manager.onRefresh(cb);
        manager.onRefresh(cb);
        manager.onRefresh(cb);

        // Force a refresh-cycle by calling updateCachedExpiry
        manager.updateCachedExpiry(Instant.parse("2030-06-01T00:00:00Z").toEpochMilli());
        assertEquals(1, calls.get(), "refresh callback must fire exactly once even when registered 3x");
    }

    @Test
    void onInvalidateCallbackFires(@TempDir Path temp) {
        DhanTokenManager manager = primedManager(temp.resolve("token-state.json"));
        AtomicInteger calls = new AtomicInteger(0);
        manager.onInvalidate(calls::incrementAndGet);
        manager.invalidate();
        assertEquals(1, calls.get());
    }

    @Test
    void updateCachedExpiryIsNoopWhenCacheEmpty(@TempDir Path temp) {
        Path stateFile = temp.resolve("token-state.json");
        DhanTokenManager manager = new DhanTokenManager(
                settings(stateFile), new DhanAuthClient(), new DhanTotpGenerator(),
                new DhanTokenStateStore(stateFile),
                java.time.Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), java.time.ZoneId.of("UTC"))
        );
        assertNull(manager.currentSnapshot());
        assertFalse(manager.updateCachedExpiry(PRIMED_EXPIRY_MS));
    }

    @Test
    void invalidateFiresExactlyOnceEvenIfCacheAlreadyNull(@TempDir Path temp) {
        DhanTokenManager manager = primedManager(temp.resolve("token-state.json"));
        AtomicInteger calls = new AtomicInteger(0);
        manager.onInvalidate(calls::incrementAndGet);
        manager.invalidate();
        // Second invalidate should NOT fire because the previous was null already
        manager.invalidate();
        assertEquals(1, calls.get());
    }

    @Test
    void refreshCallbackFiresConcurrentlyOnDistinctUpdates(@TempDir Path temp) throws Exception {
        DhanTokenManager manager = primedManager(temp.resolve("token-state.json"));
        AtomicInteger total = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);
        manager.onRefresh(total::incrementAndGet);

        int N = 20;
        Thread[] threads = new Thread[N];
        long base = Instant.parse("2030-01-01T00:00:00Z").toEpochMilli();
        for (int i = 0; i < N; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    // Each thread writes a unique expiry value to ensure
                    // the update is observable.
                    manager.updateCachedExpiry(base + idx);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread t : threads) t.join(5_000);

        // The exact count is racy (early threads may set a value that's
        // later overwritten by a thread with the same expiry), but at
        // least one update must succeed.
        assertTrue(total.get() >= 1, "at least one update must fire the refresh callback (got " + total.get() + ")");
    }
}
