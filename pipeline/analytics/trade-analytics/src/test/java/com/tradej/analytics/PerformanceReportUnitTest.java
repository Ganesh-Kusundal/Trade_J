package com.tradej.analytics;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class PerformanceReportUnitTest {

    @Test
    void emptyFactoryCreatesZeroedReport() {
        PerformanceReport report = PerformanceReport.empty("exec-1");
        assertEquals("exec-1", report.executionId());
        assertEquals(BigDecimal.ZERO, report.sharpeRatio());
    }

    @Test
    void recordCarriesAllFields() {
        PerformanceReport report = new PerformanceReport(
                UUID.randomUUID().toString(),
                "exec-1",
                10, 6, 4,
                new BigDecimal("12.5"),
                new BigDecimal("1.2"),
                new BigDecimal("0.9"),
                new BigDecimal("-15.0"),
                new BigDecimal("60.0"),
                new BigDecimal("1.5"),
                new BigDecimal("0.3"),
                java.util.Map.of("volatility", new BigDecimal("20.0")),
                Instant.now()
        );
        assertEquals(10, report.totalTrades());
        assertEquals(6, report.winningTrades());
    }
}
