package com.tradej.execution.risk;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.core.testing.ConcurrentStressTester;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stress tests for atomicity of loss counters in {@link PositionRiskHandler}.
 */
@Tag("stress")
class PositionRiskHandlerStressTest {

    private static NetPositionProvider emptyPositions() {
        return Map::of;
    }

    /**
     * Verifies that concurrent TradeClosed events correctly accumulate
     * realized losses without lost updates (the original volatile long bug).
     */
    @Test
    void concurrentTradeClosedAccumulatesLossesCorrectly() {
        RiskLimits limits = new RiskLimits(1_000_000_000L, 100, 1_000_000_000L, 100);
        PositionRiskHandler handler = new PositionRiskHandler(limits, emptyPositions());

        int workers = 10;
        int tradesPerWorker = 100;
        long lossPerTrade = 100L;

        var result = ConcurrentStressTester.run(workers, tradesPerWorker, threadIndex -> {
            handler.onDomainEvent(new TradeClosed(
                    EventMetadata.root(),
                    "t-" + threadIndex + "-" + tradesPerWorker,
                    "SBIN",
                    75_000L,
                    -lossPerTrade, 10L,
                    "stop_loss"
            ), e -> {});
        });

        result.assertAllPassed().requireNoExceptions();

        long expected = (long) workers * tradesPerWorker * lossPerTrade;
        assertEquals(expected, handler.getRealizedLossPaisa(),
                "Total realized loss should be exactly " + expected);
    }

    /**
     * Verifies that concurrent increment on AtomicInteger (consecutive losses)
     * is correct under contention.
     */
    @Test
    void concurrentLossSequenceIsCorrect() {
        RiskLimits limits = new RiskLimits(1_000_000_000L, 100, 1_000_000_000L, 100);
        PositionRiskHandler handler = new PositionRiskHandler(limits, emptyPositions());

        int workers = 10;
        int opsPerWorker = 200;

        var result = ConcurrentStressTester.run(workers, opsPerWorker, threadIndex -> {
            handler.onDomainEvent(new TradeClosed(
                    EventMetadata.root(),
                    "t-" + threadIndex,
                    "SBIN",
                    75_000L,
                    -100L, 10L,
                    "stop_loss"
            ), e -> {});
        });

        result.assertAllPassed().requireNoExceptions();

        int expected = workers * opsPerWorker;
        assertEquals(expected, handler.getConsecutiveLosses(),
                "Total consecutive losses should be exactly " + expected);
    }

    /**
     * Verifies that kill switch reset clears both counters.
     */
    @Test
    void resetDailyLimitsClearsState() {
        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.conservative(),
                emptyPositions()
        );

        handler.onDomainEvent(new TradeClosed(
                EventMetadata.root(),
                "t-1",
                "SBIN",
                75_000L,
                -500L, 10L,
                "stop_loss"
        ), e -> {});

        assertTrue(handler.getRealizedLossPaisa() > 0, "Should have accumulated losses");
        assertTrue(handler.getConsecutiveLosses() > 0, "Should have consecutive losses");

        handler.resetDailyLimits();

        assertEquals(0L, handler.getRealizedLossPaisa(),
                "Realized loss should be cleared after reset");
        assertEquals(0, handler.getConsecutiveLosses(),
                "Consecutive losses should be cleared after reset");
        assertFalse(handler.isKillSwitchActive(),
                "Kill switch should be cleared after reset");
    }

    /**
     * Verifies that concurrent SignalPendingExecution qualification does not
     * corrupt position state.
     */
    @Test
    void concurrentSignalQualificationIsConsistent() {
        NetPositionProvider provider = () -> Map.of("SBIN", 0L);

        RiskLimits limits = new RiskLimits(1_000_000_000L, 100, 1_000_000_000L, 100);
        PositionRiskHandler handler = new PositionRiskHandler(limits, provider);

        int workers = 10;
        int signalsPerWorker = 100;

        var result = ConcurrentStressTester.run(workers, signalsPerWorker, threadIndex -> {
            List<DomainEvent> emitted = new ArrayList<>();
            handler.onDomainEvent(new SignalPendingExecution(
                    EventMetadata.root(),
                    "sig-" + threadIndex,
                    new OrderRequest(
                            "SBIN",
                            ExchangeSegment.NSE_EQ,
                            Side.BUY,
                            1L,
                            OrderType.LIMIT,
                            75_000L,
                            0L,
                            ProductType.INTRADAY,
                            Validity.DAY,
                            "sig-" + threadIndex
                    ),
                    Map.of("thread", threadIndex)
            ), emitted::add);
        });

        result.assertAllPassed().requireNoExceptions();
    }
}
