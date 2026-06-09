package com.tradej.historical.ingest.canonical;

import com.tradej.historical.ingest.calendar.TradingCalendarStore;
import com.tradej.historical.ingest.sync.GapDetector;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class HistoricalDataPlatformCertificationTest {

    private final TradingCalendarStore calendar = new TradingCalendarStore();
    private final GapDetector gapDetector = new GapDetector(calendar);

    // ── Trading Calendar ──────────────────────────────────────────────

    @Test
    void weekendsAreNonTradingDays() {
        // 2026-06-06 is Saturday, 2026-06-07 is Sunday
        assertFalse(calendar.isTradingDay(ExchangeSegment.NSE_EQ, LocalDate.of(2026, 6, 6)));
        assertFalse(calendar.isTradingDay(ExchangeSegment.NSE_EQ, LocalDate.of(2026, 6, 7)));
    }

    @Test
    void weekdaysAreTradingDays() {
        // 2026-06-08 is Monday
        assertTrue(calendar.isTradingDay(ExchangeSegment.NSE_EQ, LocalDate.of(2026, 6, 8)));
    }

    @Test
    void nseHolidaysAreNonTradingDays() {
        assertFalse(calendar.isTradingDay(ExchangeSegment.NSE_EQ, LocalDate.of(2026, 1, 26)));
        assertFalse(calendar.isTradingDay(ExchangeSegment.NSE_EQ, LocalDate.of(2026, 8, 15)));
        assertFalse(calendar.isTradingDay(ExchangeSegment.NSE_EQ, LocalDate.of(2026, 10, 2)));
    }

    @Test
    void tradingDaysCountIsCorrect() {
        var days = calendar.tradingDays(ExchangeSegment.NSE_EQ,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertTrue(days.size() >= 20 && days.size() <= 23,
                "June 2026 should have 20-23 trading days, got " + days.size());
    }

    @Test
    void nextTradingDaySkipsWeekend() {
        // Friday → Monday
        assertEquals(LocalDate.of(2026, 6, 8),
                calendar.nextTradingDay(ExchangeSegment.NSE_EQ, LocalDate.of(2026, 6, 5)));
    }

    // ── Gap Detection ─────────────────────────────────────────────────

    @Test
    void expectedTimestamps1mGeneratesCorrectCount() {
        var timestamps = gapDetector.expectedBarTimestamps1m(
                ExchangeSegment.NSE_EQ,
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 8));
        assertEquals(GapDetector.barsPerTradingDay1m(), timestamps.size(),
                "One trading day should have " + GapDetector.barsPerTradingDay1m() + " 1m bars");
    }

    @Test
    void expectedTimestamps5mGeneratesCorrectCount() {
        var timestamps = gapDetector.expectedBarTimestamps(
                ExchangeSegment.NSE_EQ, "5m",
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 8));
        assertEquals(75, timestamps.size(),
                "One trading day should have 75 5m bars (375/5)");
    }

    @Test
    void detectGapsFindsMissingTimestamps() {
        var allTimestamps = gapDetector.expectedBarTimestamps1m(
                ExchangeSegment.NSE_EQ,
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 8));
        // Remove 10 timestamps to simulate gaps
        var actual = new java.util.HashSet<>(allTimestamps);
        var iterator = actual.iterator();
        for (int i = 0; i < 10 && iterator.hasNext(); i++) {
            iterator.next();
            iterator.remove();
        }
        var gaps = gapDetector.detectGaps(ExchangeSegment.NSE_EQ, "1m",
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 8), actual);
        assertEquals(10, gaps.size());
    }

    @Test
    void noGapsWhenAllTimestampsPresent() {
        var allTimestamps = gapDetector.expectedBarTimestamps1m(
                ExchangeSegment.NSE_EQ,
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 8));
        var gaps = gapDetector.detectGaps(ExchangeSegment.NSE_EQ, "1m",
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 8),
                new java.util.HashSet<>(allTimestamps));
        assertTrue(gaps.isEmpty());
    }

    // ── Canonical Paths ───────────────────────────────────────────────

    @Test
    void barsPathFollowsHivePartitioning() {
        var path = CanonicalPaths.barsPath(
                java.nio.file.Path.of("/data"), "NSE_EQ", "RELIANCE", "1m", 2026, 6);
        assertEquals("/data/historical/bars/segment=NSE_EQ/symbol=RELIANCE/interval=1m/year=2026/month=06",
                path.toString());
    }

    @Test
    void monthPartitionPadsSingleDigit() {
        assertEquals("06", CanonicalPaths.monthPartition(LocalDate.of(2026, 6, 8)));
        assertEquals("12", CanonicalPaths.monthPartition(LocalDate.of(2026, 12, 1)));
    }

    // ── Candle Schema ─────────────────────────────────────────────────

    @Test
    void candleHasOiAndTradesFields() {
        var candle = new com.tradej.core.domain.model.Candle(
                "RELIANCE", "1m", 1000L, 2000L, 100L, 110L, 90L, 105L, 5000L, true, 1000L, 50L);
        assertEquals(1000L, candle.oi());
        assertEquals(50L, candle.trades());
    }

    @Test
    void candleBackwardCompatConstructorDefaultsOiAndTradesToZero() {
        var candle = new com.tradej.core.domain.model.Candle(
                "RELIANCE", "1m", 1000L, 2000L, 100L, 110L, 90L, 105L, 5000L, true);
        assertEquals(0L, candle.oi());
        assertEquals(0L, candle.trades());
    }

    // ── Data Quality Report ───────────────────────────────────────────

    @Test
    void dataQualityReportIsCompleteWhenNoMissingBars() {
        var report = new HistoricalDataStore.DataQualityReport(
                "SBIN", "1m", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 8),
                100, 100, 0, 100.0, java.util.List.of());
        assertTrue(report.isComplete());
    }

    @Test
    void dataQualityReportIsIncompleteWhenMissingBars() {
        var report = new HistoricalDataStore.DataQualityReport(
                "SBIN", "1m", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 8),
                100, 95, 5, 95.0, java.util.List.of(LocalDate.of(2026, 6, 5)));
        assertFalse(report.isComplete());
    }
}
