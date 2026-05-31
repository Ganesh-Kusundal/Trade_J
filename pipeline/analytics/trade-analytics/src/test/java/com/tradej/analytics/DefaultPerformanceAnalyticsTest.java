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
}