package com.tradej.analytics;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PerformanceReportTest {

    @Test
    void emptyReportHasZeroValues() {
        PerformanceReport report = PerformanceReport.empty("exec-123");

        assertNotNull(report.reportId());
        assertEquals("exec-123", report.executionId());
        assertEquals(0, report.totalTrades());
        assertEquals(0, report.winningTrades());
        assertEquals(0, report.losingTrades());
        assertEquals(BigDecimal.ZERO, report.totalReturnPct());
        assertEquals(BigDecimal.ZERO, report.sharpeRatio());
        assertEquals(BigDecimal.ZERO, report.sortinoRatio());
        assertEquals(BigDecimal.ZERO, report.maxDrawdownPct());
        assertEquals(BigDecimal.ZERO, report.winRatePct());
        assertEquals(BigDecimal.ZERO, report.profitFactor());
        assertEquals(BigDecimal.ZERO, report.expectancy());
        assertTrue(report.extraMetrics().isEmpty());
        assertNotNull(report.generatedAt());
    }

    @Test
    void fullReportConstruction() {
        Instant now = Instant.now();

        PerformanceReport report = new PerformanceReport(
                "report-1",
                "exec-1",
                100,
                60,
                40,
                BigDecimal.valueOf(25.5),
                BigDecimal.valueOf(1.85),
                BigDecimal.valueOf(2.1),
                BigDecimal.valueOf(-8.3),
                BigDecimal.valueOf(60.0),
                BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(255.0),
                Map.of("calmar", BigDecimal.valueOf(3.07)),
                now
        );

        assertEquals("report-1", report.reportId());
        assertEquals("exec-1", report.executionId());
        assertEquals(100, report.totalTrades());
        assertEquals(60, report.winningTrades());
        assertEquals(40, report.losingTrades());
        assertEquals(BigDecimal.valueOf(25.5), report.totalReturnPct());
        assertEquals(BigDecimal.valueOf(1.85), report.sharpeRatio());
        assertEquals(BigDecimal.valueOf(3.07), report.extraMetrics().get("calmar"));
    }

    @Test
    void emptyReportWithNullExecutionId() {
        PerformanceReport report = PerformanceReport.empty(null);
        assertNull(report.executionId());
    }
}