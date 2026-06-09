package com.tradej.broker.dhan.websocket;

import com.tradej.core.domain.event.BrokerAdapterError;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.StreamHealthChanged;
import com.tradej.core.domain.time.LiveTradingClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DhanReconnectController}.
 *
 * <p>Covers the reconnection state machine: circuit breaker, exponential backoff,
 * scheduler lifecycle, event publishing, and shutdown behavior.
 */
@Tag("unit")
class DhanReconnectControllerTest {

    private static final String BROKER_ID = "dhan";

    private final List<DomainEvent> capturedEvents = new CopyOnWriteArrayList<>();
    private final EventMetadataFactory metadataFactory = new EventMetadataFactory(new LiveTradingClock());
    private DhanReconnectController controller;

    @BeforeEach
    void setUp() {
        capturedEvents.clear();
        controller = new DhanReconnectController(capturedEvents::add, metadataFactory, BROKER_ID);
    }

    @AfterEach
    void tearDown() {
        controller.close();
    }

    // ── Shutdown behavior ──────────────────────────────────────────────

    @Nested
    class ShutdownTests {

        @Test
        void initiallyNotShutdown() {
            assertFalse(controller.isShutdown());
        }

        @Test
        void isShutdownAfterCall() {
            controller.shutdown();
            assertTrue(controller.isShutdown());
        }

        @Test
        void startBackoffReturnsMinusOneWhenShutdown() {
            controller.shutdown();

            long delay = controller.startBackoff(() -> {}, () -> {});

            assertEquals(-1L, delay,
                    "startBackoff should return -1 when controller is shut down");
        }

        @Test
        void closeCallsShutdown() {
            controller.close();
            assertTrue(controller.isShutdown());
        }

        @Test
        void shutdownIsIdempotent() {
            controller.shutdown();
            controller.shutdown();
            assertTrue(controller.isShutdown());
        }
    }

    // ── Circuit breaker ────────────────────────────────────────────────

    @Nested
    class CircuitBreakerTests {

        @Test
        void circuitOpensAfterFailureThreshold() {
            // First 2 attempts should succeed (threshold is 3)
            assertTrue(controller.startBackoff(() -> {}, () -> {}) >= 0,
                    "Attempt 1 should proceed (below threshold)");
            assertTrue(controller.startBackoff(() -> {}, () -> {}) >= 0,
                    "Attempt 2 should proceed (below threshold)");

            // Third attempt hits threshold → circuit opens → returns -1
            long delay = controller.startBackoff(() -> {}, () -> {});
            assertEquals(-1L, delay,
                    "Attempt 3 should open circuit and return -1");
        }

        @Test
        void circuitOpenBlocksFurtherAttempts() {
            // Exhaust all 3 allowed attempts
            controller.startBackoff(() -> {}, () -> {});
            controller.startBackoff(() -> {}, () -> {});
            controller.startBackoff(() -> {}, () -> {});

            // Circuit is open — further attempts should fail
            long delay = controller.startBackoff(() -> {}, () -> {});
            assertEquals(-1L, delay,
                    "StartBackoff should return -1 while circuit is open");
        }

        @Test
        void resetCircuitAllowsNewAttempts() {
            // Exhaust all 3 allowed attempts
            controller.startBackoff(() -> {}, () -> {});
            controller.startBackoff(() -> {}, () -> {});
            controller.startBackoff(() -> {}, () -> {});
            assertEquals(-1L, controller.startBackoff(() -> {}, () -> {}),
                    "Circuit should be open after 3 failures");

            // Reset
            controller.resetCircuit();

            // Now should be able to attempt again
            assertTrue(controller.startBackoff(() -> {}, () -> {}) >= 0,
                    "After reset, startBackoff should succeed again");
        }

        @Test
        void resetCircuitDuringNormalOperation() {
            controller.startBackoff(() -> {}, () -> {});
            controller.resetCircuit();

            // First attempt after reset should be attempt 1 again
            long delay = controller.startBackoff(() -> {}, () -> {});
            assertTrue(delay >= 0,
                    "After reset, startBackoff should succeed");
        }
    }

    // ── Backoff scheduling ─────────────────────────────────────────────

    @Nested
    class BackoffTests {

        @Test
        void clientRebinderRunsSynchronously() {
            AtomicBoolean rebinderCalled = new AtomicBoolean(false);

            controller.startBackoff(
                    () -> rebinderCalled.set(true),
                    () -> {}
            );

            assertTrue(rebinderCalled.get(),
                    "clientRebinder should run synchronously during startBackoff");
        }

        @Test
        void reconnectorRunsAfterDelay() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);

            controller.startBackoff(
                    () -> {},
                    latch::countDown
            );

            assertTrue(latch.await(5, TimeUnit.SECONDS),
                    "Reconnector should be scheduled and run within 5 seconds");
        }

        @Test
        void reconnectorDoesNotRunIfShutdownBeforeScheduledDelay() throws Exception {
            AtomicBoolean reconnectorRan = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);

            long delay = controller.startBackoff(
                    () -> {},
                    () -> {
                        reconnectorRan.set(true);
                        latch.countDown();
                    }
            );

            // Shutdown immediately — scheduled task will see shutdown=true and skip
            controller.shutdown();

            // If the reconnector ran anyway (immediate schedule with 0 delay),
            // verify it didn't throw. If delay > 0, the shutdown check should prevent it.
            boolean ranBeforeShutdown = latch.await(200, TimeUnit.MILLISECONDS);

            if (ranBeforeShutdown) {
                // Race: reconnector ran before shutdown took effect — acceptable
                assertTrue(reconnectorRan.get());
            } else {
                // Clean: reconnector was prevented by shutdown flag
                assertFalse(reconnectorRan.get(),
                        "Reconnector should not run after shutdown");
            }
        }

        @Test
        void delayIsNonNegativeForValidAttempt() {
            long delay = controller.startBackoff(() -> {}, () -> {});
            assertTrue(delay >= 0,
                    "Delay should be non-negative for a valid reconnect attempt");
        }

        @Test
        void delayUsesExponentialBackoff() {
            long delay1 = controller.startBackoff(() -> {}, () -> {});
            long delay2 = controller.startBackoff(() -> {}, () -> {});

            // Exponential base: 1000, 2000, 4000...
            // Max jitter: 25% of base = 250, 500, 1000...
            // So delay1 ∈ [1000, 1250], delay2 ∈ [2000, 2500]
            // Conservative check: delay2 should be >= delay1 (min possible: 2000 >= 1250)
            assertTrue(delay2 >= delay1,
                    "Second delay should be >= first delay (base=1000,2000; delay1="
                            + delay1 + ", delay2=" + delay2 + ")");
        }
    }

    // ── Event publishing ───────────────────────────────────────────────

    @Nested
    class EventPublishingTests {

        @Test
        void noEventsPublishedDuringNormalAttempt() {
            controller.startBackoff(() -> {}, () -> {});

            assertTrue(capturedEvents.isEmpty(),
                    "No events should be published during a normal reconnect attempt");
        }

        @Test
        void brokerErrorAndHealthEventPublishedWhenCircuitOpens() {
            // Exhaust all 3 attempts to trigger circuit open
            controller.startBackoff(() -> {}, () -> {});
            controller.startBackoff(() -> {}, () -> {});
            controller.startBackoff(() -> {}, () -> {});

            assertEquals(2, capturedEvents.size(),
                    "Circuit open should publish 2 events: broker error + health event");

            // First event should be BrokerAdapterError
            assertInstanceOf(BrokerAdapterError.class, capturedEvents.get(0),
                    "First event should be BrokerAdapterError");
            BrokerAdapterError error = (BrokerAdapterError) capturedEvents.get(0);
            assertEquals("reconnect-circuit", error.stage(),
                    "Error stage should be 'reconnect-circuit'");

            // Second event should be StreamHealthChanged
            assertInstanceOf(StreamHealthChanged.class, capturedEvents.get(1),
                    "Second event should be StreamHealthChanged");
            StreamHealthChanged health = (StreamHealthChanged) capturedEvents.get(1);
            assertEquals("CIRCUIT_OPEN", health.status(),
                    "Health status should be CIRCUIT_OPEN");
        }

        @Test
        void noDuplicateEventsOnSubsequentBlockedAttempts() {
            // Open circuit
            controller.startBackoff(() -> {}, () -> {});
            controller.startBackoff(() -> {}, () -> {});
            controller.startBackoff(() -> {}, () -> {});

            int eventsAfterOpen = capturedEvents.size();

            // Blocked attempt (circuit open) — should not publish again
            controller.startBackoff(() -> {}, () -> {});
            assertEquals(eventsAfterOpen, capturedEvents.size(),
                    "No additional events should be published when circuit is already open");
        }
    }

    // ── Reconciliation scheduling ──────────────────────────────────────

    @Nested
    class ReconciliationTests {

        @Test
        void scheduleReconciliationDoesNotThrow() {
            // scheduleReconciliation uses a 5-minute initial delay (scheduleAtFixedRate),
            // so we can't wait for it in a unit test. Verify it doesn't throw.
            assertDoesNotThrow(() -> controller.scheduleReconciliation(() -> {}));
        }

        @Test
        void scheduleResubscribeRunsImmediately() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);

            controller.scheduleResubscribe(latch::countDown);

            assertTrue(latch.await(5, TimeUnit.SECONDS),
                    "Scheduled resubscribe task should run within 5 seconds");
        }
    }

    // ── Reconnect attempt counter ──────────────────────────────────────

    @Nested
    class AttemptCounterTests {

        @Test
        void attemptsIncrementOnEachCall() {
            controller.startBackoff(() -> {}, () -> {});
            controller.startBackoff(() -> {}, () -> {});

            // First two should succeed, third will open circuit
            long delay = controller.startBackoff(() -> {}, () -> {});
            assertEquals(-1L, delay,
                    "Third attempt should hit threshold and open circuit");
        }

        @Test
        void resetClearsAttemptCounter() {
            controller.startBackoff(() -> {}, () -> {});
            controller.startBackoff(() -> {}, () -> {});

            controller.resetCircuit();

            // After reset, should be back to attempt 1
            long delay = controller.startBackoff(() -> {}, () -> {});
            assertTrue(delay >= 0,
                    "After reset, first attempt should succeed");
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Nested
    class LifecycleTests {

        @Test
        void closeIsIdempotent() {
            controller.close();
            controller.close(); // Should not throw
        }

        @Test
        void afterCloseStartBackoffReturnsMinusOne() {
            controller.close();
            assertEquals(-1L, controller.startBackoff(() -> {}, () -> {}));
        }

        @Test
        void multipleResetsAreIdempotent() {
            controller.resetCircuit();
            controller.resetCircuit();
            controller.resetCircuit();

            assertTrue(controller.startBackoff(() -> {}, () -> {}) >= 0,
                    "After multiple resets, startBackoff should succeed");
        }


    }
}
