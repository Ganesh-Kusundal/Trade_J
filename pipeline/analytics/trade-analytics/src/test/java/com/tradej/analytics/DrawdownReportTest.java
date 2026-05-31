package com.tradej.analytics;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DrawdownReportTest {

    @Test
    void drawdownReportConstruction() {
        EquityPoint ep1 = new EquityPoint(Instant.parse("2024-01-01T10:00:00Z"), BigDecimal.valueOf(100000));
        EquityPoint ep2 = new EquityPoint(Instant.parse("2024-01-01T11:00:00Z"), BigDecimal.valueOf(95000));
        EquityPoint ep3 = new EquityPoint(Instant.parse("2024-01-01T12:00:00Z"), BigDecimal.valueOf(92000));

        DrawdownReport report = new DrawdownReport(
                BigDecimal.valueOf(-8.0),
                BigDecimal.valueOf(-8000),
                BigDecimal.valueOf(-2.5),
                List.of(ep1, ep2, ep3),
                Duration.ofHours(2)
        );

        assertEquals(BigDecimal.valueOf(-8.0), report.maxDrawdownPct());
        assertEquals(BigDecimal.valueOf(-8000), report.maxDrawdownAbsolute());
        assertEquals(BigDecimal.valueOf(-2.5), report.currentDrawdownPct());
        assertEquals(3, report.underwaterEquityCurve().size());
        assertEquals(Duration.ofHours(2), report.maxDrawdownDuration());
    }

    @Test
    void equityPointConstruction() {
        Instant ts = Instant.now();
        EquityPoint point = new EquityPoint(ts, BigDecimal.valueOf(105000));

        assertEquals(ts, point.timestamp());
        assertEquals(BigDecimal.valueOf(105000), point.equityValue());
    }

    @Test
    void emptyUnderwaterCurveIsValid() {
        DrawdownReport report = new DrawdownReport(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                Duration.ZERO
        );

        assertTrue(report.underwaterEquityCurve().isEmpty());
        assertEquals(BigDecimal.ZERO, report.maxDrawdownPct());
    }
}