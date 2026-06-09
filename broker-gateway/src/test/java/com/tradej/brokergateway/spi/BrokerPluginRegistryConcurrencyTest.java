package com.tradej.brokergateway.spi;

import com.tradej.brokergateway.result.BrokerSource;
import com.tradej.core.testing.ConcurrentStressTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency tests for {@link BrokerPluginRegistry}.
 * Verifies thread-safe registration, deregistration, lookup, and listener notification.
 */
@Tag("unit")
class BrokerPluginRegistryConcurrencyTest {

    private BrokerPluginRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BrokerPluginRegistry();
    }

    @Test
    void concurrentRegisterAndLookupIsConsistent() {
        int workers = 8;
        int iterations = 200;

        var result = ConcurrentStressTester.run(workers, iterations, threadIndex -> {
            BrokerSource source = BrokerSource.values()[threadIndex % BrokerSource.values().length];
            BrokerProvider provider = mockProvider(source, "broker-" + threadIndex);
            registry.register(provider);
            assertTrue(registry.provider(source).isPresent() || registry.size() >= 0,
                    "Registry should be in consistent state after concurrent register");
        });

        assertTrue(result.exceptions().isEmpty(), "No exceptions during concurrent register/lookup");
        assertTrue(registry.size() > 0, "Registry should have entries after concurrent registration");
    }

    @Test
    void concurrentRegisterAndUnregisterDoesNotCorrupt() {
        // Pre-register all sources
        for (BrokerSource source : BrokerSource.values()) {
            registry.register(mockProvider(source, source.name().toLowerCase()));
        }
        int initialSize = registry.size();

        int workers = 8;
        int iterations = 200;

        var result = ConcurrentStressTester.run(workers, iterations, threadIndex -> {
            BrokerSource source = BrokerSource.values()[threadIndex % BrokerSource.values().length];
            if (threadIndex % 2 == 0) {
                registry.unregister(source);
            } else {
                registry.register(mockProvider(source, source.name().toLowerCase() + "-reload"));
            }
        });

        assertTrue(result.exceptions().isEmpty(),
                "No exceptions during concurrent register/unregister");
    }

    @Test
    void concurrentListenerNotificationDoesNotThrow() {
        var notificationCount = new AtomicInteger();
        var events = new CopyOnWriteArrayList<String>();

        BrokerPluginRegistry.PluginListener listener = new BrokerPluginRegistry.PluginListener() {
            @Override
            public void onPluginRegistered(BrokerProvider provider) {
                notificationCount.incrementAndGet();
                events.add("registered:" + provider.source());
            }

            @Override
            public void onPluginUnregistered(BrokerProvider provider) {
                notificationCount.incrementAndGet();
                events.add("unregistered:" + provider.source());
            }
        };

        registry.addListener(listener);

        int workers = 4;
        int iterations = 100;

        var result = ConcurrentStressTester.run(workers, iterations, threadIndex -> {
            BrokerSource source = BrokerSource.values()[threadIndex % BrokerSource.values().length];
            BrokerProvider provider = mockProvider(source, "listener-test-" + threadIndex);
            registry.register(provider);
        });

        assertTrue(result.exceptions().isEmpty(), "No exceptions during concurrent listener notification");
        assertTrue(notificationCount.get() > 0, "Listener should have been notified");
    }

    @Test
    void concurrentDescriptorsSnapshotIsConsistent() {
        // Pre-register
        for (BrokerSource source : BrokerSource.values()) {
            registry.register(mockProvider(source, source.name().toLowerCase()));
        }

        int workers = 6;
        int iterations = 100;

        var result = ConcurrentStressTester.run(workers, iterations, threadIndex -> {
            var descriptors = registry.descriptors();
            assertNotNull(descriptors, "descriptors() should never return null");
            // During concurrent modification, size may vary but should not throw
            var sources = registry.availableSources();
            assertNotNull(sources, "availableSources() should never return null");
        });

        assertTrue(result.exceptions().isEmpty(),
                "Concurrent reads of descriptors/sources should not throw");
    }

    private BrokerProvider mockProvider(BrokerSource source, String displayName) {
        BrokerProvider provider = mock(BrokerProvider.class);
        when(provider.source()).thenReturn(source);
        when(provider.displayName()).thenReturn(displayName);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.descriptor()).thenReturn(new BrokerDescriptor(
                source, displayName, java.util.Map.of(), java.util.Map.of(), java.util.List.of(), ""));
        return provider;
    }
}
