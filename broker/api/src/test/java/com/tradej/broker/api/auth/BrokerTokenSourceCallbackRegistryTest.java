package com.tradej.broker.api.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link BrokerTokenSource.CallbackRegistry} helper,
 * which deduplicates listeners by reference identity.
 */
@Tag("unit")
class BrokerTokenSourceCallbackRegistryTest {

    @Test
    void refreshCallbackFiresOnceEvenWithDuplicateRegistration() {
        BrokerTokenSource.CallbackRegistry registry = new BrokerTokenSource.CallbackRegistry();
        AtomicInteger calls = new AtomicInteger(0);
        Runnable cb = calls::incrementAndGet;
        registry.onRefresh(cb);
        registry.onRefresh(cb);
        registry.onRefresh(cb);

        registry.fireRefresh();
        registry.fireRefresh();

        assertEquals(2, calls.get(),
                "duplicate registrations are coalesced; same callback fired twice (once per fireRefresh)");
    }

    @Test
    void invalidateCallbackFiresOnceEvenWithDuplicateRegistration() {
        BrokerTokenSource.CallbackRegistry registry = new BrokerTokenSource.CallbackRegistry();
        AtomicInteger calls = new AtomicInteger(0);
        Runnable cb = calls::incrementAndGet;
        registry.onInvalidate(cb);
        registry.onInvalidate(cb);

        registry.fireInvalidate();
        assertEquals(1, calls.get());
    }

    @Test
    void nullRegistrationsAreIgnored() {
        BrokerTokenSource.CallbackRegistry registry = new BrokerTokenSource.CallbackRegistry();
        registry.onRefresh(null);
        registry.onInvalidate(null);
        assertEquals(0, registry.refreshListenerCount());
        assertEquals(0, registry.invalidateListenerCount());
        registry.fireRefresh();
        registry.fireInvalidate();
        // No exception thrown
    }

    @Test
    void listenerExceptionsDoNotBreakOtherListeners() {
        BrokerTokenSource.CallbackRegistry registry = new BrokerTokenSource.CallbackRegistry();
        AtomicInteger beforeCount = new AtomicInteger(0);
        AtomicInteger afterCount = new AtomicInteger(0);

        registry.onRefresh(() -> {
            throw new RuntimeException("boom");
        });
        registry.onRefresh(beforeCount::incrementAndGet);
        registry.onRefresh(afterCount::incrementAndGet);

        registry.fireRefresh();

        assertEquals(1, beforeCount.get(), "listener after the throwing one must still run");
        assertEquals(1, afterCount.get());
    }
}
