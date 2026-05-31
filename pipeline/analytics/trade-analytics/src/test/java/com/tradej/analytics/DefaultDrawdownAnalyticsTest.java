package com.tradej.analytics;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DefaultDrawdownAnalyticsTest {

    private final DefaultDrawdownAnalytics analytics = new DefaultDrawdownAnalytics();

    @Test
    void analyzeEmptyCurve() {
        DrawdownReport report = analytics.analyze(List.of());

        assertEquals(BigDecimal.ZERO, report.maxDrawdownPct());
        assertEquals(BigDecimal.ZERO, report.maxDrawdownAbsolute());
        assertTrue(report.underwaterEquityCurve().isEmpty());
    }

    @Test
    void analyzeSinglePoint() {
        EquityPoint point = new EquityPoint(Instant.now(), BigDecimal.valueOf(100000));
        DrawdownReport report = analytics.analyze(List.of(point));

        assertEquals(BigDecimal.ZERO, report.maxDrawdownPct());
        assertEquals(BigDecimal.ZERO, report.maxDrawdownAbsolute());
        assertEquals(1, report.underwaterEquityCurve().size());
    }

    @Test
    void analyzeNoDrawdown() {
        Instant now = Instant.now();
        List<EquityPoint> curve = List.of(
                new EquityPoint(now, BigDecimal.valueOf(100000)),
                new EquityPoint(now.plusSeconds(60), BigDecimal.valueOf(105000)),
                new EquityPoint(now.plusSeconds(120), BigDecimal.valueOf(110000))
        );

        DrawdownReport report = analytics.analyze(curve);

        assertEquals(BigDecimal.ZERO, report.maxDrawdownPct());
        assertEquals(BigDecimal.ZERO, report.maxDrawdownAbsolute());
    }

    @Test
    void analyzeWithDrawdown() {
        Instant now = Instant.now();
        List<EquityPoint> curve = List.of(
                new EquityPoint(now, BigDecimal.valueOf(100000)),
                new EquityPoint(now.plusSeconds(60), BigDecimal.valueOf(110000)),
                new EquityPoint(now.plusSeconds(120), BigDecimal.valueOf(105000)),
                new EquityPoint(now.plusSeconds(180), BigDecimal.valueOf(95000)),
                new EquityPoint(now.plusSeconds(240), BigDecimal.valueOf(100000))
        );

        DrawdownReport report = analytics.analyze(curve);

        assertTrue(report.maxDrawdownPct().compareTo(BigDecimal.ZERO) < 0);
        assertTrue(report.maxDrawdownAbsolute().compareTo(BigDecimal.ZERO) < 0);
        assertTrue(report.maxDrawdownDuration().compareTo(Duration.ZERO) > 0);
    }

    @Test
    void analyzeDecliningCurve() {
        Instant now = Instant.now();
        List<EquityPoint> curve = List.of(
                new EquityPoint(now, BigDecimal.valueOf(100000)),
                new EquityPoint(now.plusSeconds(60), BigDecimal.valueOf(90000)),
                new EquityPoint(now.plusSeconds(120), BigDecimal.valueOf(80000))
        );

        DrawdownReport report = analytics.analyze(curve);

        assertNotNull(report);
        assertTrue(report.maxDrawdownPct().compareTo(BigDecimal.ZERO) < 0,
                "maxDrawdownPct should be negative, got: " + report.maxDrawdownPct());
        assertTrue(report.maxDrawdownAbsolute().compareTo(BigDecimal.ZERO) < 0,
                "maxDrawdownAbsolute should be negative, got: " + report.maxDrawdownAbsolute());
    }
}