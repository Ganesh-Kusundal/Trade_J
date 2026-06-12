package com.tradej.broker.core.auth;

import com.tradej.broker.api.auth.TokenLifecycleService;
import com.tradej.broker.api.auth.TokenState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the LOW-3 dedup-by-reference behavior of the listener
 * registry on {@link DefaultTokenLifecycleService}.
 *
 * <p>Pre-fix, registering the same callback twice would cause it to
 * fire twice on each refresh. Now duplicate registrations are
 * coalesced by {@code BrokerTokenSource.CallbackRegistry}.
 */
@Tag("unit")
class DefaultTokenLifecycleServiceCallbackDedupTest {

    @Test
    void duplicateOnRefreshRegistrationIsCoalesced() {
        StubSubclass svc = new StubSubclass();
        AtomicInteger calls = new AtomicInteger(0);
        Runnable cb = calls::incrementAndGet;
        svc.onRefresh(cb);
        svc.onRefresh(cb);
        svc.onRefresh(cb);
        svc.fireRefresh();
        assertEquals(1, calls.get(), "duplicate onRefresh registration must be coalesced");
    }

    @Test
    void distinctOnRefreshListenersAllFire() {
        StubSubclass svc = new StubSubclass();
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        AtomicInteger c = new AtomicInteger();
        svc.onRefresh(a::incrementAndGet);
        svc.onRefresh(b::incrementAndGet);
        svc.onRefresh(c::incrementAndGet);
        svc.fireRefresh();
        assertEquals(1, a.get());
        assertEquals(1, b.get());
        assertEquals(1, c.get());
    }

    @Test
    void listenerThatThrowsDoesNotPreventOthersFromRunning() {
        StubSubclass svc = new StubSubclass();
        AtomicInteger after = new AtomicInteger();
        svc.onRefresh(() -> { throw new RuntimeException("boom"); });
        svc.onRefresh(after::incrementAndGet);
        assertDoesNotThrow(svc::fireRefresh);
        assertEquals(1, after.get(), "listener after the throwing one must still run");
    }

    /**
     * Minimal concrete subclass that exposes {@code fireRefresh} for testing.
     * Uses an in-memory TokenStateStore.
     */
    private static final class StubSubclass extends DefaultTokenLifecycleService {
        StubSubclass() {
            super(new InMemoryTokenStateStore(), 60_000L);
        }

        @Override
        protected TokenState doAcquire() {
            return new TokenState("acquired", null,
                    System.currentTimeMillis() + 600_000L,
                    System.currentTimeMillis(),
                    com.tradej.broker.api.auth.TokenSource.STATIC);
        }

        @Override
        protected TokenState doRefresh(String refreshToken) {
            return new TokenState("refreshed", refreshToken,
                    System.currentTimeMillis() + 600_000L,
                    System.currentTimeMillis(),
                    com.tradej.broker.api.auth.TokenSource.STATIC);
        }

        // Expose for testing
        public void fireRefresh() {
            // Reflective: we can call the private notifyRefresh via
            // a dummy acquireToken() that uses a state with the
            // listener already registered. Simpler: just call
            // acquireToken() and let it fire.
            acquireToken();
        }
    }

    private static final class InMemoryTokenStateStore implements TokenStateStore {
        private TokenState state;

        @Override
        public TokenState load() {
            return state;
        }

        @Override
        public void save(TokenState state) {
            this.state = state;
        }
    }
}
