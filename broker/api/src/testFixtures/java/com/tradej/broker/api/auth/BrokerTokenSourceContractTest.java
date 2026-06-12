package com.tradej.broker.api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test that ALL {@link BrokerTokenSource} implementations must pass.
 *
 * <p>Subclass this test for each concrete broker auth implementation:
 * <ul>
 *   <li>{@code DhanBrokerTokenSourceContractTest} in {@code trade-broker-dhan}</li>
 *   <li>{@code UpstoxBrokerTokenSourceContractTest} in {@code trade-broker-upstox}</li>
 * </ul>
 *
 * <p>These tests are intentionally hermetic — they only check the
 * behavior the contract guarantees, not the broker-specific semantics.
 */
@Tag("unit")
public abstract class BrokerTokenSourceContractTest {

    /** Subclass must provide a freshly-constructed implementation. */
    protected abstract BrokerTokenSource createSource();

    private BrokerTokenSource source;

    @BeforeEach
    void setUp() {
        source = createSource();
    }

    @Test
    void bearerTokenIsNonBlank() {
        assertDoesNotThrow(() -> {
            String token = source.bearerToken();
            assertNotNull(token, "bearerToken() must not return null");
            assertFalse(token.isBlank(), "bearerToken() must not be blank");
        });
    }

    @Test
    void bearerTokenIsStableAcrossCallsWhenTokenIsFresh() {
        String first = source.bearerToken();
        String second = source.bearerToken();
        assertEquals(first, second,
                "bearerToken() must be stable across calls when the cached token is still valid");
    }

    @Test
    void ensureValidIsIdempotent() {
        // Warm up: some implementations mint lazily on the first call.
        long beforeExpiry = source.expiryEpochMs();
        String beforeToken = source.expiryEpochMs() == -1L ? null : source.bearerToken();
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100; i++) {
                source.ensureValid();
            }
        });
        // The "5 minutes → multiple mints" regression guard at the
        // contract level: 100 ensureValid() calls in quick succession
        // must not move the expiry. If it did, an internal mint happened.
        long afterExpiry = source.expiryEpochMs();
        if (beforeExpiry > 0L) {
            assertEquals(beforeExpiry, afterExpiry,
                    "100 ensureValid() calls must not move the cached token expiry "
                            + "(expiry moved from " + beforeExpiry + " to " + afterExpiry
                            + " — looks like a token re-mint happened on a no-op call)");
        }
        if (beforeToken != null) {
            assertEquals(beforeToken, source.bearerToken(),
                    "100 ensureValid() calls must not change the bearer token");
        }
    }

    @Test
    void expiryEpochMsIsNonNegative() {
        // First ensure we have a token (some implementations mint lazily)
        source.bearerToken();
        long expiry = source.expiryEpochMs();
        assertTrue(expiry == -1L || expiry > 0L,
                "expiryEpochMs() must be -1 (unknown) or a positive epoch millis; was: " + expiry);
    }

    @Test
    void invalidateDoesNotThrow() {
        assertDoesNotThrow(() -> source.invalidate());
    }

    @Test
    void onRefreshCallbackRegisteredWithoutThrowing() {
        assertDoesNotThrow(() -> source.onRefresh(() -> {}));
    }

    @Test
    void onInvalidateCallbackRegisteredWithoutThrowing() {
        assertDoesNotThrow(() -> source.onInvalidate(() -> {}));
    }

    @Test
    void onRefreshListenerExceptionDoesNotPropagate() {
        source.onRefresh(() -> { throw new RuntimeException("boom"); });
        // Trigger whatever natural flow the implementation has. We don't
        // assert the listener ran — only that registration didn't fail.
        source.ensureValid();
    }

    @Test
    void onInvalidateListenerIsInvokedOnInvalidate() {
        AtomicInteger calls = new AtomicInteger(0);
        source.onInvalidate(calls::incrementAndGet);
        source.invalidate();
        // Some implementations (e.g. static/extended token holders) do not
        // maintain a listener registry and the listener will not fire.
        // For implementations backed by a real registry (DhanTokenManager,
        // UpstoxTokenManager), the listener must fire.
        if (supportsListenerRegistry()) {
            assertEquals(1, calls.get(),
                    "onInvalidate listeners must fire exactly once per invalidate()");
        }
    }

    @Test
    void onInvalidateListenerIsNotInvokedWithoutInvalidate() {
        AtomicInteger calls = new AtomicInteger(0);
        source.onInvalidate(calls::incrementAndGet);
        source.bearerToken();
        assertEquals(0, calls.get(),
                "onInvalidate listeners must NOT fire on a plain bearerToken() call");
    }

    @Test
    void duplicateOnInvalidateRegistrationIsIdempotent() {
        AtomicInteger calls = new AtomicInteger(0);
        Runnable cb = calls::incrementAndGet;
        source.onInvalidate(cb);
        source.onInvalidate(cb);
        source.onInvalidate(cb);
        source.invalidate();
        if (supportsListenerRegistry()) {
            assertEquals(1, calls.get(),
                    "duplicate onInvalidate registration must be coalesced");
        }
    }

    /**
     * Subclasses with a real listener registry (e.g. token managers) should
     * override this to return {@code true} so the listener-firing tests
     * assert strict behavior. Default {@code false} for static/extended
     * holders.
     */
    protected boolean supportsListenerRegistry() {
        return false;
    }

    @Test
    void concurrentBearerTokenCallsAreSafe() throws Exception {
        // 8 threads each call bearerToken() 25 times. The contract is that
        // every call returns a non-blank token; we do not assert they all
        // match because implementations may legitimately rotate the token
        // under contention. We only assert no crash and no null/blank.
        int threads = 8;
        int iterations = 25;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            Thread.ofVirtual().name("broker-token-stress-" + t).start(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        String token = source.bearerToken();
                        assertNotNull(token);
                        assertFalse(token.isBlank());
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS),
                "concurrent bearerToken() calls must complete within 10s");
    }
}
