package com.tradej.app.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the GracefulShutdownCoordinator lifecycle and hook execution.
 */
@Tag("unit")
class GracefulShutdownCoordinatorTest {

    private GracefulShutdownCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new GracefulShutdownCoordinator(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("isRunning returns true initially")
    void isRunningInitially() {
        assertTrue(coordinator.isRunning());
    }

    @Test
    @DisplayName("isRunning returns false after shutdown")
    void isRunningFalseAfterShutdown() {
        coordinator.onApplicationShutdown(new ContextClosedEvent(new GenericApplicationContext()));
        assertFalse(coordinator.isRunning());
    }

    @Test
    @DisplayName("Registered hook is called on shutdown")
    void hookCalledOnShutdown() {
        AtomicBoolean hookCalled = new AtomicBoolean(false);
        coordinator.register(remaining -> hookCalled.set(true));

        coordinator.onApplicationShutdown(new ContextClosedEvent(new GenericApplicationContext()));

        assertTrue(hookCalled.get(), "Registered hook should be called during shutdown");
    }

    @Test
    @DisplayName("Multiple hooks are called in registration order")
    void multipleHooksCalledInOrder() {
        List<String> executionOrder = new ArrayList<>();

        coordinator.register(new GracefulShutdownCoordinator.ShutdownHook() {
            @Override public void shutdown(Duration remaining) { executionOrder.add("first"); }
            @Override public String name() { return "first"; }
        });
        coordinator.register(new GracefulShutdownCoordinator.ShutdownHook() {
            @Override public void shutdown(Duration remaining) { executionOrder.add("second"); }
            @Override public String name() { return "second"; }
        });
        coordinator.register(new GracefulShutdownCoordinator.ShutdownHook() {
            @Override public void shutdown(Duration remaining) { executionOrder.add("third"); }
            @Override public String name() { return "third"; }
        });

        coordinator.onApplicationShutdown(new ContextClosedEvent(new GenericApplicationContext()));

        assertEquals(List.of("first", "second", "third"), executionOrder,
                "Hooks should execute in registration order");
    }

    @Test
    @DisplayName("Failing hook does not prevent other hooks from running")
    void failingHookDoesNotBlockOthers() {
        AtomicBoolean thirdHookCalled = new AtomicBoolean(false);

        coordinator.register(new GracefulShutdownCoordinator.ShutdownHook() {
            @Override public void shutdown(Duration remaining) { /* first hook ok */ }
            @Override public String name() { return "first"; }
        });
        coordinator.register(new GracefulShutdownCoordinator.ShutdownHook() {
            @Override public void shutdown(Duration remaining) { throw new RuntimeException("boom"); }
            @Override public String name() { return "failing"; }
        });
        coordinator.register(new GracefulShutdownCoordinator.ShutdownHook() {
            @Override public void shutdown(Duration remaining) { thirdHookCalled.set(true); }
            @Override public String name() { return "third"; }
        });

        coordinator.onApplicationShutdown(new ContextClosedEvent(new GenericApplicationContext()));

        assertTrue(thirdHookCalled.get(),
                "Third hook should still run even after second hook throws");
    }

    @Test
    @DisplayName("Hooks receive remaining Duration")
    void hooksReceiveRemainingDuration() {
        AtomicReference<Duration> receivedDuration = new AtomicReference<>();

        coordinator.register(remaining -> receivedDuration.set(remaining));

        coordinator.onApplicationShutdown(new ContextClosedEvent(new GenericApplicationContext()));

        assertNotNull(receivedDuration.get(), "Hook should receive a Duration");
        assertTrue(receivedDuration.get().toSeconds() > 0,
                "Remaining duration should be positive");
        assertTrue(receivedDuration.get().toSeconds() <= 10,
                "Remaining duration should not exceed shutdown timeout");
    }

    @Test
    @DisplayName("start() resets running state")
    void startResetsRunning() {
        coordinator.onApplicationShutdown(new ContextClosedEvent(new GenericApplicationContext()));
        assertFalse(coordinator.isRunning());

        coordinator.start();
        assertTrue(coordinator.isRunning(), "start() should reset running to true");
    }

    @Test
    @DisplayName("getPhase returns MAX_VALUE for last-phase shutdown")
    void phaseIsMaxValue() {
        assertEquals(Integer.MAX_VALUE, coordinator.getPhase(),
                "Phase should be MAX_VALUE to ensure last-phase shutdown");
    }

    @Test
    @DisplayName("Shutdown with no hooks completes without error")
    void shutdownWithNoHooks() {
        assertDoesNotThrow(() ->
                coordinator.onApplicationShutdown(new ContextClosedEvent(new GenericApplicationContext()))
        );
        assertFalse(coordinator.isRunning());
    }
}
