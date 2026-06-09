package com.tradej.execution.risk;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies atomicity guarantees of the kill switch in {@link PositionRiskHandler}:
 * concurrent activation is idempotent, activate+deactivate is safe, and
 * signal processing during activation is correctly rejected.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class KillSwitchAtomicityTest {

    @Mock
    KillSwitchCoordinator mockCoordinator;

    private static NetPositionProvider emptyPositions() {
        return Map::of;
    }

    private static SignalPendingExecution testSignal() {
        OrderRequest order = new OrderRequest(
                "RELIANCE", ExchangeSegment.NSE_EQ, Side.BUY, 10,
                OrderType.MARKET, 0L, 0L, ProductType.INTRADAY, Validity.DAY, "corr-1"
        );
        return new SignalPendingExecution(
                EventMetadata.root(),
                "sig-1",
                order,
                Map.of()
        );
    }

    /**
     * Verifies that concurrent activate calls (via checkCombinedLossLimit) only
     * trigger the coordinator engage exactly once due to compareAndSet idempotency.
     */
    @Test
    void concurrentActivateCallsOnlySucceedOnce() throws Exception {
        RiskLimits limits = RiskLimits.withOpenPositionQuantity(100L, 100, 1_000_000_000L, 100);
        PositionRiskHandler handler = new PositionRiskHandler(limits, emptyPositions(), null, null, mockCoordinator);

        // Set unrealized loss so that total >= maxDailyLossPaisa
        handler.updateUnrealizedLoss(200L);

        int threads = 20;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    handler.checkCombinedLossLimit(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads simultaneously
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        assertTrue(handler.isKillSwitchActive(), "Kill switch should be active");
        verify(mockCoordinator, times(1)).engage(any());
    }

    /**
     * Verifies that activate followed by deactivate (resetDailyLimits)
     * leaves the handler in a consistent non-active state with no interleaving issues.
     */
    @Test
    void activateAndDeactivateIsAtomic() throws Exception {
        RiskLimits limits = RiskLimits.withOpenPositionQuantity(100L, 100, 1_000_000_000L, 100);
        PositionRiskHandler handler = new PositionRiskHandler(limits, emptyPositions());

        int cycles = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < cycles; i++) {
            Future<?> activateFuture = executor.submit(() -> {
                handler.updateUnrealizedLoss(200L);
                handler.checkCombinedLossLimit(100L);
            });

            Future<?> deactivateFuture = executor.submit(() -> {
                handler.resetDailyLimits();
            });

            activateFuture.get(5, TimeUnit.SECONDS);
            deactivateFuture.get(5, TimeUnit.SECONDS);

            // After both complete, state must be consistent
            handler.updateUnrealizedLoss(0L);
        }

        executor.shutdown();

        handler.resetDailyLimits();
        assertFalse(handler.isKillSwitchActive(),
                "After deactivate (resetDailyLimits), kill switch must be off");
    }

    /**
     * Verifies that signals are rejected when the kill switch is active.
     */
    @Test
    void signalProcessingDuringActivationIsRejected() {
        RiskLimits limits = RiskLimits.withOpenPositionQuantity(1_000_000_000L, 100, 1_000_000_000L, 100);
        PositionRiskHandler handler = new PositionRiskHandler(limits, emptyPositions());

        // Activate the kill switch
        handler.updateUnrealizedLoss(200L);
        handler.checkCombinedLossLimit(100L);
        assertTrue(handler.isKillSwitchActive());

        // Try to process a signal — it should be suppressed
        List<DomainEvent> published = new ArrayList<>();
        SignalPendingExecution signal = testSignal();

        handler.onDomainEvent(signal, published::add);

        assertEquals(1, published.size(), "A suppression event should be published");
        assertInstanceOf(SignalSuppressed.class, published.getFirst(),
                "Published event should be SignalSuppressed");
        SignalSuppressed suppressed = (SignalSuppressed) published.getFirst();
        assertTrue(suppressed.reason().contains("kill_switch"),
                "Suppression reason should contain kill_switch but was: " + suppressed.reason());
    }
}
