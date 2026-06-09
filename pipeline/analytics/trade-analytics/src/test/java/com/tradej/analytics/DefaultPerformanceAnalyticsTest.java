package com.tradej.analytics;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DefaultPerformanceAnalyticsTest {

    private final DefaultPerformanceAnalytics analytics = new DefaultPerformanceAnalytics();

    @Test
    void analyzeEmptyTrades() {
        PerformanceReport report = analytics.analyzeTrades(List.of());

        assertNotNull(report);
        assertEquals(0, report.totalTrades());
        assertEquals(BigDecimal.ZERO, report.totalReturnPct());
    }

    @Test
    void analyzeWinningTrades() {
        List<TradeRecord> trades = List.of(
                new TradeRecord("1", "NIFTY", Instant.now(), Instant.now(),
                        BigDecimal.valueOf(100), BigDecimal.valueOf(105), BigDecimal.ONE,
                        BigDecimal.valueOf(5), BigDecimal.valueOf(5.0), "LONG"),
                new TradeRecord("2", "NIFTY", Instant.now(), Instant.now(),
                        BigDecimal.valueOf(100), BigDecimal.valueOf(110), BigDecimal.ONE,
                        BigDecimal.valueOf(10), BigDecimal.valueOf(10.0), "LONG")
        );

        PerformanceReport report = analytics.analyzeTrades(trades);

        assertEquals(2, report.totalTrades());
        assertEquals(2, report.winningTrades());
        assertEquals(0, report.losingTrades());
        assertTrue(report.winRatePct().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(report.sharpeRatio().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void analyzeMixedTrades() {
        List<TradeRecord> trades = List.of(
                new TradeRecord("1", "NIFTY", Instant.now(), Instant.now(),
                        BigDecimal.valueOf(100), BigDecimal.valueOf(105), BigDecimal.ONE,
                        BigDecimal.valueOf(5), BigDecimal.valueOf(5.0), "LONG"),
                new TradeRecord("2", "NIFTY", Instant.now(), Instant.now(),
                        BigDecimal.valueOf(100), BigDecimal.valueOf(95), BigDecimal.ONE,
                        BigDecimal.valueOf(-5), BigDecimal.valueOf(-5.0), "LONG")
        );

        PerformanceReport report = analytics.analyzeTrades(trades);

        assertEquals(2, report.totalTrades());
        assertEquals(1, report.winningTrades());
        assertEquals(1, report.losingTrades());
        assertEquals(BigDecimal.valueOf(50.0).setScale(1, java.math.RoundingMode.HALF_UP),
                    report.winRatePct().setScale(1, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void analyzeAllLosingTrades() {
        List<TradeRecord> trades = List.of(
                new TradeRecord("1", "NIFTY", Instant.now(), Instant.now(),
                        BigDecimal.valueOf(100), BigDecimal.valueOf(95), BigDecimal.ONE,
                        BigDecimal.valueOf(-5), BigDecimal.valueOf(-5.0), "LONG"),
                new TradeRecord("2", "NIFTY", Instant.now(), Instant.now(),
                        BigDecimal.valueOf(100), BigDecimal.valueOf(90), BigDecimal.ONE,
                        BigDecimal.valueOf(-10), BigDecimal.valueOf(-10.0), "LONG")
        );

        PerformanceReport report = analytics.analyzeTrades(trades);

        assertEquals(2, report.totalTrades());
        assertEquals(0, report.winningTrades());
        assertEquals(2, report.losingTrades());
        assertEquals(BigDecimal.ZERO, report.winRatePct());
        assertTrue(report.profitFactor().compareTo(BigDecimal.ONE) <= 0);
    }

    @Test
    void analyzeNullTrades() {
        PerformanceReport report = analytics.analyzeTrades(null);
        assertNotNull(report);
        assertEquals(0, report.totalTrades());
    }

    @Test
    void expectancyCalculation() {
        List<TradeRecord> trades = List.of(
                new TradeRecord("1", "NIFTY", Instant.now(), Instant.now(),
                        BigDecimal.valueOf(100), BigDecimal.valueOf(105), BigDecimal.ONE,
                        BigDecimal.valueOf(5), BigDecimal.valueOf(5.0), "LONG"),
                new TradeRecord("2", "NIFTY", Instant.now(), Instant.now(),
                        BigDecimal.valueOf(100), BigDecimal.valueOf(95), BigDecimal.ONE,
                        BigDecimal.valueOf(-5), BigDecimal.valueOf(-5.0), "LONG")
        );

        PerformanceReport report = analytics.analyzeTrades(trades);

        assertNotNull(report.expectancy());
        assertNotNull(report.extraMetrics().get("profitFactor"));
    }

    @Test
    void testTradeAccumulatorFrom() {
        TradeRecord winning = new TradeRecord("1", "NIFTY", Instant.now(), Instant.now(),
                BigDecimal.valueOf(100), BigDecimal.valueOf(105), BigDecimal.ONE,
                BigDecimal.valueOf(5), BigDecimal.valueOf(5.0), "LONG");
        DefaultPerformanceAnalytics.TradeAccumulator accWin = DefaultPerformanceAnalytics.TradeAccumulator.from(winning);
        assertEquals(1, accWin.totalTrades());
        assertEquals(1, accWin.winningTrades());
        assertEquals(0, accWin.losingTrades());
        assertEquals(BigDecimal.valueOf(5), accWin.totalReturn());
        assertEquals(BigDecimal.valueOf(5), accWin.totalPositiveReturn());
        assertEquals(BigDecimal.ZERO, accWin.totalNegativeReturn());
        assertEquals(1, accWin.returns().size());

        TradeRecord losing = new TradeRecord("2", "NIFTY", Instant.now(), Instant.now(),
                BigDecimal.valueOf(100), BigDecimal.valueOf(95), BigDecimal.ONE,
                BigDecimal.valueOf(-5), BigDecimal.valueOf(-5.0), "LONG");
        DefaultPerformanceAnalytics.TradeAccumulator accLoss = DefaultPerformanceAnalytics.TradeAccumulator.from(losing);
        assertEquals(1, accLoss.totalTrades());
        assertEquals(0, accLoss.winningTrades());
        assertEquals(1, accLoss.losingTrades());
        assertEquals(BigDecimal.valueOf(-5), accLoss.totalReturn());
        assertEquals(BigDecimal.ZERO, accLoss.totalPositiveReturn());
        assertEquals(BigDecimal.valueOf(5), accLoss.totalNegativeReturn());

        TradeRecord flat = new TradeRecord("3", "NIFTY", Instant.now(), Instant.now(),
                BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.ONE,
                BigDecimal.valueOf(0), BigDecimal.valueOf(0.0), "LONG");
        DefaultPerformanceAnalytics.TradeAccumulator accFlat = DefaultPerformanceAnalytics.TradeAccumulator.from(flat);
        assertEquals(1, accFlat.totalTrades());
        assertEquals(0, accFlat.winningTrades());
        assertEquals(0, accFlat.losingTrades());
        assertEquals(BigDecimal.ZERO, accFlat.totalReturn());
        assertEquals(BigDecimal.ZERO, accFlat.totalPositiveReturn());
        assertEquals(BigDecimal.ZERO, accFlat.totalNegativeReturn());
    }

    @Test
    void testTradeAccumulatorMerge() {
        TradeRecord winning = new TradeRecord("1", "NIFTY", Instant.now(), Instant.now(),
                BigDecimal.valueOf(100), BigDecimal.valueOf(105), BigDecimal.ONE,
                BigDecimal.valueOf(5), BigDecimal.valueOf(5.0), "LONG");
        TradeRecord losing = new TradeRecord("2", "NIFTY", Instant.now(), Instant.now(),
                BigDecimal.valueOf(100), BigDecimal.valueOf(95), BigDecimal.ONE,
                BigDecimal.valueOf(-5), BigDecimal.valueOf(-5.0), "LONG");

        DefaultPerformanceAnalytics.TradeAccumulator acc1 = DefaultPerformanceAnalytics.TradeAccumulator.from(winning);
        DefaultPerformanceAnalytics.TradeAccumulator acc2 = DefaultPerformanceAnalytics.TradeAccumulator.from(losing);
        DefaultPerformanceAnalytics.TradeAccumulator merged = acc1.merge(acc2);

        assertEquals(2, merged.totalTrades());
        assertEquals(1, merged.winningTrades());
        assertEquals(1, merged.losingTrades());
        assertEquals(BigDecimal.ZERO, merged.totalReturn());
        assertEquals(BigDecimal.valueOf(5), merged.totalPositiveReturn());
        assertEquals(BigDecimal.valueOf(5), merged.totalNegativeReturn());
        assertEquals(2, merged.returns().size());
    }
}