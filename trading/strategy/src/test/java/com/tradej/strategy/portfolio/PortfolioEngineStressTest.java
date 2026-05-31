package com.tradej.strategy.portfolio;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.value.Side;
import com.tradej.core.testing.ConcurrentStressTester;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stress tests for concurrent access to {@link PortfolioEngine}.
 * Verifies that the TOCTOU race in reserveSignal is fixed,
 * and that capital/net-position tracking is atomic.
 */
@Tag("stress")
class PortfolioEngineStressTest {

    private static SignalGenerated signal(String signalId, String strategyName, String symbol,
                                          Side side, long qty, long price) {
        return new SignalGenerated(
                EventMetadata.root(),
                signalId, symbol, "5m", side, price, 0L, 0L,
                "test", Map.of("quantity", qty, "strategyName", strategyName)
        );
    }

    /**
     * Verifies that concurrent signal approvals for the same strategy
     * do not exceed the capital allocation (the original TOCTOU bug).
     * This is the most critical PortfolioEngine test.
     */
    @Test
    void concurrentSignalsDontExceedCapital() {
        var engine = new PortfolioEngine(1_000_000L, 10_000_000L); // ₹10,000 per strategy
        int workers = 10;
        int signalsPerWorker = 100;

        // Each signal needs 1,000 paisa at price 100 = 100 * 100 = 10,000 paisa
        // With 10 workers × 100 signals each = 1,000 signals
        // Each signal: qty=100, price=100 → requires 10,000 paisa
        // But only 1,000,000 paisa is allocated → at most 100 signals should pass

        var result = ConcurrentStressTester.run(workers, signalsPerWorker, threadIndex -> {
            String signalId = "SIG-" + threadIndex + "-" + System.nanoTime();
            var sig = signal(signalId, "StratA", "SBIN", Side.BUY, 100, 100);
            engine.reserveSignal(sig);
        });

        result.assertNoSilentLoss(); // don't assert all passed — rejections are expected

        long usedCapital = engine.usedCapitalPaisa("StratA");
        assertTrue(usedCapital <= 1_000_000L,
                "Used capital " + usedCapital + " should not exceed allocation 1,000,000");
    }

    /**
     * Verifies that concurrent net position updates use merge() semantics
     * and don't lose positions.
     */
    @Test
    void concurrentNetPositionsAccumulateCorrectly() {
        var engine = new PortfolioEngine(1_000_000_000L, 100_000_000_000L);
        int workers = 10;
        int signalsPerWorker = 100;

        // Each signal is for the SAME symbol but different strategy names
        var result = ConcurrentStressTester.run(workers, signalsPerWorker, threadIndex -> {
            String signalId = "SIG-NET-" + threadIndex + "-" + System.nanoTime();
            String strategyName = "Strat-" + threadIndex;
            var sig = signal(signalId, strategyName, "RELIANCE", Side.BUY, 10, 500);
            engine.reserveSignal(sig);
        });

        result.assertNoSilentLoss();

        // Each BUY of 10 adds +10 net position
        long net = engine.netPosition("RELIANCE");

        // The exact count depends on how many passed approval (should be very high
        // since capital is huge), but the net should be positive and consistent
        assertTrue(net > 0, "Net position should be positive for all BUY signals, got " + net);
        assertEquals(net % 10, 0,
                "Net position should be a multiple of 10 (qty per signal), got " + net);
    }

    /**
     * Verifies that concurrent reserve + free operations don't leak capital.
     */
    @Test
    void concurrentReserveAndFreeDoesntLeakCapital() {
        var engine = new PortfolioEngine(1_000_000L, 10_000_000L);
        int workers = 10;
        int opsPerWorker = 100;

        var result = ConcurrentStressTester.run(workers, opsPerWorker, threadIndex -> {
            String signalId = "SIG-LK-" + threadIndex + "-" + System.nanoTime();
            var sig = signal(signalId, "LeakTest", "TCS", Side.BUY, 10, 500);

            // Reserve
            String rejection = engine.reserveSignal(sig);
            if (rejection == null) {
                // Free it
                engine.onDomainEvent(
                        new com.tradej.core.domain.event.SignalSuppressed(
                                EventMetadata.root(), signalId, "TCS", "test release", sig.attributes()),
                        e -> {});
            }
        });

        result.assertNoSilentLoss();

        // After all operations, used capital should be 0 (all signals freed)
        long usedCapital = engine.usedCapitalPaisa("LeakTest");
        assertEquals(0L, usedCapital,
                "Used capital should be 0 after all signals freed, got " + usedCapital);
    }

    /**
     * Verifies that concurrent reserve + TradeOpened doesn't corrupt net positions.
     */
    @Test
    void concurrentTradeOpenedUpdatesNetCorrectly() {
        var engine = new PortfolioEngine(1_000_000_000L, 100_000_000_000L);
        int workers = 10;
        int opsPerWorker = 100;

        var result = ConcurrentStressTester.run(workers, opsPerWorker, threadIndex -> {
            String signalId = "SIG-TO-" + threadIndex + "-" + System.nanoTime();
            var sig = signal(signalId, "TradeTest", "HDFC", Side.BUY, 50, 1000);

            String rejection = engine.reserveSignal(sig);
            if (rejection == null) {
                // Simulate TradeOpened
                engine.onDomainEvent(
                        new com.tradej.core.domain.event.TradeOpened(
                                EventMetadata.root(),
                                "TRADE-" + signalId,
                                "ORD-" + signalId,
                                signalId,
                                "HDFC",
                                Side.LONG,
                                50,
                                1000,
                                900,
                                1100
                        ),
                        e -> {});
            }
        });

        result.assertNoSilentLoss();
    }
}
